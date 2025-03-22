package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.DTO.UserDTO;
import crm.dopaflow_backend.Model.StatutUser;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsersController {

    private final UserService userService;
    @GetMapping("/search")
    public ResponseEntity<List<UserDTO>> searchUsers(@RequestParam("search") String searchTerm) {
        List<User> users = userService.searchUsers(searchTerm);
        List<UserDTO> userDTOs = users.stream().map(this::mapToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(userDTOs);
    }

    private UserDTO mapToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setProfilePhotoUrl(user.getProfilePhotoUrl());
        return dto;
    }
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(user);
    }
    @GetMapping("/{id}/photo")
    public ResponseEntity<String> getUserPhoto(@PathVariable Long id) {
        try {
            User user = userService.getUserById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String photoUrl = user.getProfilePhotoUrl(); // Assuming photo is a String URL in User model
            if (photoUrl == null || photoUrl.isEmpty()) {
                return ResponseEntity.ok("https://i.sstatic.net/l60Hf.png"); // Default placeholder
            }

            return ResponseEntity.ok(photoUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
    @GetMapping("/all")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        try {
            List<Map<String, Object>> users = userService.getAllUsersWithActivity();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping("/create")
    public ResponseEntity<?> createUser(@RequestBody User user) {
        try {
            User createdUser = userService.createUser(
                    user.getUsername(),
                    user.getEmail(),
                    user.getPassword(),
                    user.getRole(),
                    user.getBirthdate()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/edit/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            User updatedUser = userService.updateUser(id, user);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/block")
    public ResponseEntity<?> blockUser(@RequestBody Map<String, Long> request) {
        try {
            Long id = request.get("id");
            if (id == null) {
                throw new RuntimeException("User ID is required");
            }
            User user = userService.getUserById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setStatus(StatutUser.Suspended);
            userService.saveUser(user);
            return ResponseEntity.ok(Map.of("message", "User suspended successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activateUser(@RequestBody Map<String, Long> request) {
        try {
            Long id = request.get("id");
            if (id == null) {
                throw new RuntimeException("User ID is required");
            }
            User user = userService.getUserById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setStatus(StatutUser.Active);
            userService.saveUser(user);
            return ResponseEntity.ok(Map.of("message", "User activated successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}