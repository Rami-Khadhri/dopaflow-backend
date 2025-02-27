// AuthService.java (Updated)
package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.DTO.AuthDTO;
import crm.dopaflow_backend.Model.LoginHistory;
import crm.dopaflow_backend.Model.StatutUser;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Security.JwtUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TwoFactorAuthService twoFactorService;
    private final PasswordEncoder passwordEncoder;
    @Autowired
    private JavaMailSender mailSender;
    @Value("${frontend.url}")
    private String frontendUrl;
    public Map<String, Object> authenticateUser(AuthDTO.LoginRequest request, HttpServletRequest httpRequest) {
        User user = userService.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        if (!user.getVerified()) {
            throw new RuntimeException("Email not verified");
        }
        if (user.getStatus().equals(StatutUser.Suspended)) {
            throw new RuntimeException("Account is suspended,Contact an admin");
        }
        // Record login details
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        String location = getLocationFromIp(ipAddress);

        LoginHistory loginHistory = LoginHistory.builder()
                .ipAddress(ipAddress)
                .location(location)
                .deviceInfo(userAgent)
                .loginTime(new Date())
                .build();

        user.addLoginHistory(loginHistory);
        user.setLastLogin(new Date());
        userService.saveUser(user);

        Map<String, Object> response = new HashMap<>();
        if (user.isTwoFactorEnabled()) {
            String tempToken = jwtUtil.generateTempToken(user.getEmail());
            response.put("requires2FA", true);
            response.put("tempToken", tempToken);
        } else {
            String token = jwtUtil.generateToken(user.getEmail());
            response.put("requires2FA", false);
            response.put("token", token);
        }
        return response;
    }

    public String verify2FACode(String tempToken, int code) {
        if (!tempToken.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid token format");
        }
        String token = tempToken.substring(7);
        if (!jwtUtil.validateToken(token) || !jwtUtil.isTempToken(token)) {
            throw new RuntimeException("Invalid or expired temporary token");
        }

        String email = jwtUtil.extractEmail(token);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!twoFactorService.verifyCode(user.getTwoFactorSecret(), code)) {
            throw new RuntimeException("Invalid 2FA code");
        }

        return jwtUtil.generateToken(user.getEmail());
    }

    private String getLocationFromIp(String ipAddress) {
        try {
            URL url = new URL("http://ip-api.com/json/" + ipAddress);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response = in.readLine();
            in.close();
            // Simple parsing - in production, use a JSON parser
            if (response.contains("city")) {
                return response.substring(response.indexOf("\"city\":\"") + 8,
                        response.indexOf("\"", response.indexOf("\"city\":\"") + 8));
            }
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }


    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        try {
            // Create a MIME message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set email details
            helper.setTo(toEmail);
            helper.setSubject("Reset Your DopaFlow Password");

            // Create the reset link
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

            // HTML email body
            String htmlContent = """
                <html>
                    <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                        <div style="max-width: 600px; margin: auto; border: 1px solid #ddd; border-radius: 10px; padding: 20px; background-color: #f9f9f9;">
                            <h2 style="color: #0056b3; text-align: center;">Reset Your DopaFlow Password</h2>
                            <p>We received a request to reset your password. Click the button below to set a new password:</p>
                            <div style="text-align: center; margin: 20px 0;">
                                <a href="%s" style="display: inline-block; background-color: #0056b3; color: white; padding: 10px 20px; text-decoration: none; font-size: 16px; border-radius: 5px;">Reset Password</a>
                            </div>
                            <p>If the button above doesn't work, copy and paste the following link into your browser:</p>
                            <p style="word-break: break-word;"><a href="%s">%s</a></p>
                            <p>This link will expire in 1 hour. If you didn’t request a password reset, please ignore this email or contact support.</p>
                            <p>Thank you,<br/>The DopaFlow Team</p>
                        </div>
                    </body>
                </html>
            """.formatted(resetLink, resetLink, resetLink);

            // Set the HTML content
            helper.setText(htmlContent, true);

            // Send the email
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Error sending password reset email: " + e.getMessage());
        }
    }
    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }
}