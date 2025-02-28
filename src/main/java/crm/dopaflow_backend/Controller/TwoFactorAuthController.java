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
@CrossOrigin(origins = "http://localhost:3000")
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
            String secret = twoFactorService.generateSecretKey();
            user.setTwoFactorSecret(secret);
            userService.saveUser(user);
            String qrCodeUrl = twoFactorService.getQRCodeUrl(user.getEmail(), secret);
            // Return both QR code URL and secret key
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
                boolean wasEnabled = user.isTwoFactorEnabled();
                user.setTwoFactorEnabled(true);
                userService.saveUser(user);

                // Notify user based on action (enabling or disabling 2FA)
                if (!wasEnabled) {
                    notificationService.createNotification(
                            user,
                            "Two-Factor Authentication has been enabled for your account.",
                            Notification.NotificationType.TWO_FA_ENABLED
                    );
                } else {
                    notificationService.createNotification(
                            user,
                            "Two-Factor Authentication has been disabled for your account.",
                            Notification.NotificationType.TWO_FA_DISABLED
                    );
                }
                return ResponseEntity.ok(Map.of("message", "2FA " + (wasEnabled ? "disabled" : "enabled") + " successfully"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid code"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid code format"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access denied: Invalid or missing token"));
        }
    }
}