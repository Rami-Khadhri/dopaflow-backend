package crm.dopaflow_backend.Security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}") // Default to 24 hours if not specified
    private long expirationTime;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 bytes (256 bits) for HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a JWT token for a given email.
     * @param email The user's email as the subject of the token.
     * @return A compact JWT string.
     */
    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a temporary JWT token with a 5-minute expiration.
     * @param email The user's email as the subject of the token.
     * @return A compact JWT string.
     */
    public String generateTempToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 300_000)) // 5 minutes
                .claim("temp", true)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extracts the email (subject) from a JWT token.
     * @param token The JWT token string.
     * @return The email extracted from the token.
     */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Validates a JWT token.
     * @param token The JWT token string.
     * @return True if the token is valid, false otherwise.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            System.out.println("Token validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a token is a temporary token.
     * @param token The JWT token string.
     * @return True if the token is temporary, false otherwise.
     */
    public boolean isTempToken(String token) {
        Claims claims = parseClaims(token);
        Boolean isTemp = claims.get("temp", Boolean.class);
        return isTemp != null && isTemp;
    }

    /**
     * Parses the claims from a JWT token.
     * @param token The JWT token string.
     * @return The claims contained in the token.
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Extracts the email from a JWT token in the Authorization header.
     * @param authHeader The Authorization header containing the Bearer token.
     * @return The email (subject) extracted from the token.
     * @throws IllegalArgumentException if the token is invalid or missing.
     */
    public String getEmailFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid or missing token");
        }
        String token = authHeader.substring(7); // Remove "Bearer " prefix
        return extractEmail(token);
    }
}