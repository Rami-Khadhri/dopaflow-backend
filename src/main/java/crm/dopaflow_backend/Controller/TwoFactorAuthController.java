package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.User;
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

    @PostMapping("/enable")
    public ResponseEntity<?> enable2FA(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = userService.getUserFromToken(authHeader);
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
            User user = userService.getUserFromToken(authHeader);
            String code = body.get("code");
            if (code == null || code.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "2FA code is required"));
            }
            boolean isValid = twoFactorService.verifyCode(user.getTwoFactorSecret(), Integer.parseInt(code));
            if (isValid) {
                user.setTwoFactorEnabled(true);
                userService.saveUser(user);
                return ResponseEntity.ok(Map.of("message", "2FA enabled successfully"));
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