// ProfileController.java (Updated)
package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Security.JwtUtil;
import crm.dopaflow_backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ProfileController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            User user = userService.getUserFromToken(authHeader);
            Map<String, Object> profileData = Map.of(
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "twoFactorEnabled", user.isTwoFactorEnabled(),
                    "lastLogin", user.getLastLogin(),
                    "loginHistory", user.getLoginHistory().stream()
                            .map(history -> Map.of(
                                    "ipAddress", history.getIpAddress(),
                                    "location", history.getLocation() != null ? history.getLocation() : "Unknown",
                                    "deviceInfo", history.getDeviceInfo() != null ? history.getDeviceInfo() : "Unknown",
                                    "loginTime", history.getLoginTime()
                            ))
                            .collect(Collectors.toList())
            );
            // Print only essential info instead of entire object to avoid recursion
            System.out.println("Profile fetched for user: " + user.getUsername());
            return new ResponseEntity<>(profileData, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("message", "Invalid or missing token"), HttpStatus.UNAUTHORIZED);
        }
    }

    @PutMapping("/profile/update")
    public ResponseEntity<?> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> payload) {
        try {
            User user = userService.getUserFromToken(authHeader);
            String username = (String) payload.get("username");
            String currentPassword = (String) payload.get("currentPassword");
            String newPassword = (String) payload.get("newPassword");
            Boolean twoFactorEnabled = (Boolean) payload.get("twoFactorEnabled");

            User updatedUser = userService.updateUser(
                    user.getEmail(),
                    username != null && !username.isEmpty() ? username : null,
                    currentPassword,
                    newPassword,
                    twoFactorEnabled
            );
            return new ResponseEntity<>(updatedUser, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("message", "Update failed: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}