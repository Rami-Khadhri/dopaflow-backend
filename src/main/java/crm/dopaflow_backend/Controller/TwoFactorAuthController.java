package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Notification;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Security.JwtUtil;
import crm.dopaflow_backend.Service.NotificationService;
import crm.dopaflow_backend.Service.TwoFactorAuthService;
import crm.dopaflow_backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    @PostMapping("/enable")
    public ResponseEntity<?> enable2FA(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));

            // Check if 2FA is already enabled
            if (user.isTwoFactorEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("message", "2FA is already enabled"));
            }

            // Generate and set 2FA secret
            String secret = twoFactorService.generateSecretKey();
            user.setTwoFactorSecret(secret);
            user.setTwoFactorEnabled(true); // Enable 2FA here
            userService.saveUser(user);
            String link = "/profile/2fa";
            // Send notification for 2FA enabled
            notificationService.createNotification(
                    user,
                    "Two-Factor Authentication has been enabled for your account.",
                    link,
                    Notification.NotificationType.TWO_FA_ENABLED
            );

            String qrCodeUrl = twoFactorService.getQRCodeUrl(user.getUsername(), secret);
            return ResponseEntity.ok(Map.of(
                    "qrUrl", qrCodeUrl,
                    "secret", secret,
                    "message", "Scan this QR code or enter the secret manually"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access denied: Invalid or missing token"));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify2FA(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            String code = body.get("code");
            if (code == null || code.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "2FA code is required"));
            }
            boolean isValid = twoFactorService.verifyCode(user.getTwoFactorSecret(), Integer.parseInt(code));
            if (isValid) {
                return ResponseEntity.ok(Map.of("message", "Code verified successfully"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid code"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid code format"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access denied: Invalid or missing token"));
        }
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable2FA(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));

            // Check if 2FA is already disabled
            if (!user.isTwoFactorEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("message", "2FA is already disabled"));
            }

            // Verify the 2FA code
            String code = body.get("code");
            if (code == null || code.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "2FA code is required"));
            }
            boolean isValid = twoFactorService.verifyCode(user.getTwoFactorSecret(), Integer.parseInt(code));
            if (!isValid) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid 2FA code"));
            }

            // Disable 2FA
            user.setTwoFactorEnabled(false);
            user.setTwoFactorSecret(null); // Clear the secret
            userService.saveUser(user);
            String link = "/profile/2fa";
            // Send notification for 2FA disabled
            notificationService.createNotification(
                    user,
                    "Two-Factor Authentication has been disabled for your account.",
                    link,
                    Notification.NotificationType.TWO_FA_DISABLED
            );

            return ResponseEntity.ok(Map.of("message", "2FA disabled successfully"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid code format"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred: " + e.getMessage()));
        }
    }
}