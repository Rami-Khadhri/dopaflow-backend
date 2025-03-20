package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Notification;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Security.JwtUtil;
import crm.dopaflow_backend.Service.NotificationService;
import crm.dopaflow_backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);
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
    public ResponseEntity<Map<String, String>> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> payload) {
        try {
            logger.info("Received profile update request with payload: {}", payload);

            // Extract email from JWT token
            String email = jwtUtil.getEmailFromToken(authHeader);
            logger.debug("Extracted email from token: {}", email);

            // Extract fields from payload
            String username = (String) payload.get("username");
            Boolean twoFactorEnabled = (Boolean) payload.get("twoFactorEnabled");
            String birthdateStr = (String) payload.get("birthdate"); // Expecting YYYY-MM-DD

            // Parse birthdate if provided
            Date birthdate = null;
            if (birthdateStr != null && !birthdateStr.trim().isEmpty()) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    dateFormat.setLenient(false); // Strict parsing to catch invalid dates
                    birthdate = dateFormat.parse(birthdateStr);
                } catch (ParseException e) {
                    logger.warn("Invalid birthdate format: {}", birthdateStr);
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Invalid birthdate format. Use YYYY-MM-DD."));
                }
            }

            // Validate username (optional)
            if (username != null && username.trim().isEmpty()) {
                username = null; // Treat empty string as null to avoid saving ""
            }

            // Update user profile
            User updatedUser = userService.updateProfile(
                    email,
                    username, // Null if not provided or empty
                    null,     // currentPassword - not updated here
                    null,     // newPassword - not updated here
                    birthdate,
                    twoFactorEnabled // Pass through, but expect potential exception
            );

            logger.info("Profile updated successfully for email: {}", email);
            return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during profile update", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred: " + e.getMessage()));
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
                    "/profile/security",
                    Notification.NotificationType.PASSWORD_CHANGE
                        );
            return new ResponseEntity<>(Map.of("message", "Password changed successfully"), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }
}
