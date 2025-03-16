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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor

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
                    "profilePhotoUrl", user.getProfilePhotoUrl() != null ? user.getProfilePhotoUrl() : "",
                    "birthdate", user.getBirthdate(),
                    "status", user.getStatus(),
                    "verified", user.getVerified(),
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

            // Extract fields from payload
            String username = (String) payload.get("username");
            Boolean twoFactorEnabled = (Boolean) payload.get("twoFactorEnabled");
            String birthdateStr = (String) payload.get("birthdate"); // Expecting YYYY-MM-DD format from frontend

            // Parse birthdate if provided
            Date birthdate = null;
            if (birthdateStr != null && !birthdateStr.isEmpty()) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    birthdate = dateFormat.parse(birthdateStr);
                } catch (ParseException e) {
                    return new ResponseEntity<>(Map.of("error", "Invalid birthdate format. Use YYYY-MM-DD."), HttpStatus.BAD_REQUEST);
                }
            }

            // Update user with all applicable fields
            User updatedUser = userService.updateProfile(
                    user.getEmail(),
                    username != null && !username.isEmpty() ? username : null,
                    null, // Password not updated here
                    null,
                    birthdate, // Use the parsed birthdate from payload, not user.getBirthdate()
                    twoFactorEnabled
            );

            return new ResponseEntity<>(Map.of("message", "Profile updated successfully"), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "An unexpected error occurred: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
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
                    null,
                    Notification.NotificationType.PASSWORD_CHANGE
                        );
            return new ResponseEntity<>(Map.of("message", "Password changed successfully"), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }
}
