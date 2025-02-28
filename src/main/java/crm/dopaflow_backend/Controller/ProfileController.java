package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Notification;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Security.JwtUtil;
import crm.dopaflow_backend.Service.NotificationService;
import crm.dopaflow_backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ProfileController {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            Map<String, Object> profileData = Map.of(
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "role", user.getRole(),
                    "twoFactorEnabled", user.isTwoFactorEnabled(),
                    "lastLogin", user.getLastLogin(),
                    "profilePhotoUrl", user.getProfilePhotoUrl() != null ? user.getProfilePhotoUrl() : "", // Return as-is, frontend will handle base URL
                    "loginHistory", user.getLoginHistory().stream()
                            .map(history -> Map.of(
                                    "ipAddress", history.getIpAddress(),
                                    "location", history.getLocation() != null ? history.getLocation() : "Unknown",
                                    "deviceInfo", history.getDeviceInfo() != null ? history.getDeviceInfo() : "Unknown",
                                    "loginTime", history.getLoginTime()
                            ))
                            .collect(Collectors.toList())
            );
            return new ResponseEntity<>(profileData, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Invalid or missing token"), HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/profile/upload-photo")
    public ResponseEntity<?> uploadProfilePhoto(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("photo") MultipartFile photo) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            User updatedUser = userService.updateProfilePhoto(user.getEmail(), photo);
            return new ResponseEntity<>(Map.of(
                    "message", "Photo uploaded successfully",
                    "photoUrl", updatedUser.getProfilePhotoUrl()
            ), HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(Map.of("error", "Photo upload failed: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/profile/set-avatar")
    public ResponseEntity<?> setDefaultAvatar(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> payload) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            String avatarUrl = payload.get("avatarUrl");
            User updatedUser = userService.setDefaultAvatar(user.getEmail(), avatarUrl);
            return new ResponseEntity<>(Map.of(
                    "message", "Avatar set successfully",
                    "photoUrl", updatedUser.getProfilePhotoUrl()
            ), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Failed to set avatar: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/profile/update")
    public ResponseEntity<?> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> payload) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            String username = (String) payload.get("username");
            Boolean twoFactorEnabled = (Boolean) payload.get("twoFactorEnabled");

            User updatedUser = userService.updateUser(
                    user.getEmail(),
                    username != null && !username.isEmpty() ? username : null,
                    null, null, twoFactorEnabled
            );
            return new ResponseEntity<>(Map.of("message", "Profile updated successfully"), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/profile/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> payload) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            String currentPassword = payload.get("currentPassword");
            String newPassword = payload.get("newPassword");

            if (currentPassword == null || currentPassword.trim().isEmpty()) {
                return new ResponseEntity<>(Map.of("error", "Current password is required"), HttpStatus.BAD_REQUEST);
            }
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return new ResponseEntity<>(Map.of("error", "New password is required"), HttpStatus.BAD_REQUEST);
            }

            userService.changeUserPassword(user.getEmail(), currentPassword, newPassword);
            // Notify user about password change
            notificationService.createNotification(
                    user,
                    "Your password has been successfully changed.",
                    Notification.NotificationType.PASSWORD_CHANGE
            );
            return new ResponseEntity<>(Map.of("message", "Password changed successfully"), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }
}