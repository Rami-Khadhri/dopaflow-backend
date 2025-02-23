package crm.dopaflow_backend.Service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.stereotype.Service;

@Service
public class TwoFactorAuthService {

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    public String generateSecretKey() {
        return gAuth.createCredentials().getKey();
    }

    public boolean verifyCode(String secret, int code) {
        return gAuth.authorize(secret, code);
    }

    public String getQRCodeUrl(String email, String secret) {
        return "otpauth://totp/DopaFlow:" + email + "?secret=" + secret + "&issuer=DopaFlow";
    }
}