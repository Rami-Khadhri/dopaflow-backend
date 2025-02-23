package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Security.JwtUtil;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtUtil jwtUtil;

    public TokenService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String generateToken(String email) {
        return jwtUtil.generateToken(email);
    }

    public String generateTempToken(String email) {
        return jwtUtil.generateTempToken(email);
    }

    public boolean isTempToken(String token) {
        return jwtUtil.isTempToken(token);
    }
}