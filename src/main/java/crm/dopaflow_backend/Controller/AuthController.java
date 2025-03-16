package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.DTO.AuthDTO;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Service.AuthService;
import crm.dopaflow_backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDTO.RegisterRequest request) {
        try {
            User user = userService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getRole(),
                    request.getBirthdate()
            );
            Map<String, Object> response = Map.of(
                    "message", "Registration successful. Please check your email for verification",
                    "userId", user.getId()
            );
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDTO.LoginRequest request, HttpServletRequest httpRequest) {
        try {
            Map<String, Object> authResponse = authService.authenticateUser(request, httpRequest);
            System.out.println("Login response: " + authResponse);
            return new ResponseEntity<>(authResponse, HttpStatus.OK);
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<?> verify2FA(
            @RequestHeader("Authorization") String tempToken,
            @RequestBody Map<String, Integer> body) {
        try {
            String token = authService.verify2FACode(tempToken, body.get("code"));
            return new ResponseEntity<>(Map.of("token", token), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            User user = userService.verifyEmail(token);
            return new ResponseEntity<>(
                    Map.of(
                            "message", "Email verified successfully",
                            "userId", user.getId(),
                            "email", user.getEmail()
                    ),
                    HttpStatus.OK
            );
        } catch (Exception e) {
            return new ResponseEntity<>(
                    Map.of("message", "Invalid or expired verification token: " + e.getMessage()),
                    HttpStatus.BAD_REQUEST
            );
        }
    }



    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Invaild E-mail address."));

            // Generate a reset token
            String resetToken = UUID.randomUUID().toString();
            user.setVerificationToken(resetToken); // Reuse verificationToken for reset
            userService.saveUser(user);

            // Send reset email
            authService.sendPasswordResetEmail(email, resetToken);

            return new ResponseEntity<>(
                    Map.of("message", "Password reset link sent to your email"),
                    HttpStatus.OK
            );
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            String newPassword = request.get("newPassword");

            User user = userService.findByVerificationToken(token)
                    .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

            // Update password
            user.setPassword(authService.encodePassword(newPassword));
            user.setVerificationToken(null); // Clear the token after use
            userService.saveUser(user);

            return new ResponseEntity<>(
                    Map.of("message", "Password reset successful. Please login with your new password"),
                    HttpStatus.OK
            );
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return new ResponseEntity<>(Map.of("message", "Logout successful"), HttpStatus.OK);
    }

}