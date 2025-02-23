package crm.dopaflow_backend.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${frontend.url}")
    private String frontendUrl;

    public void sendVerificationEmail(String toEmail, String token) {
        try {
            // Create a MIME message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set email details
            helper.setTo(toEmail);
            helper.setSubject("Verify Your Email");

            // Create the verification link
            String verificationLink = frontendUrl + "/verify-email?token=" + token;

            // HTML email body
            String htmlContent = """
                <html>
                    <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                        <div style="max-width: 600px; margin: auto; border: 1px solid #ddd; border-radius: 10px; padding: 20px; background-color: #f9f9f9;">
                            <h2 style="color: #0056b3; text-align: center;">Welcome to DopaFlow!</h2>
                            <p>Thank you for registering with us. To complete your registration, please verify your email by clicking the button below:</p>
                            <div style="text-align: center; margin: 20px 0;">
                                <a href="%s" style="display: inline-block; background-color: #0056b3; color: white; padding: 10px 20px; text-decoration: none; font-size: 16px; border-radius: 5px;">Verify My Email</a>
                            </div>
                            <p>If the button above doesn't work, copy and paste the following link into your browser:</p>
                            <p style="word-break: break-word;"><a href="%s">%s</a></p>
                            <p>Thank you,<br/>The DopaFlow Team</p>
                        </div>
                    </body>
                </html>
            """.formatted(verificationLink, verificationLink, verificationLink);

            // Set the HTML content
            helper.setText(htmlContent, true);

            // Send the email
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Error sending email: " + e.getMessage());
        }
    }
}