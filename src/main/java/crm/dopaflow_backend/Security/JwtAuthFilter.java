package crm.dopaflow_backend.Security;

import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectProvider<UserService> userServiceProvider;

    public JwtAuthFilter(JwtUtil jwtUtil, ObjectProvider<UserService> userServiceProvider) {
        this.jwtUtil = jwtUtil;
        this.userServiceProvider = userServiceProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/login") ||
                path.startsWith("/api/auth/register") ||
                path.startsWith("/api/auth/verify-email") ||
                path.startsWith("/api/auth/forgot-password") ||
                path.startsWith("/api/auth/reset-password");


    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Log the request for debugging
        System.out.println("Processing request for: " + request.getRequestURI());
        System.out.println("Authorization header: " + request.getHeader("Authorization"));
        System.out.println("Content-Type: " + request.getContentType()); // Debug for multipart/form-data

        String token = resolveToken(request);
        if (token != null) {
            try {
                System.out.println("Validating token: " + token.substring(0, Math.min(token.length(), 10)) + "...");
                if (jwtUtil.validateToken(token)) {
                    String email = jwtUtil.extractEmail(token);
                    UserService userService = userServiceProvider.getObject();
                    User user = userService.findByEmail(email)
                            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
                    System.out.println("Authentication set for user: " + email);

                    // Set email as principal and User as details
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(email, null, user.getAuthorities());
                    authentication.setDetails(user); // Set User object in details
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); // Add request details

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    System.out.println("Token validation failed for token: " + token.substring(0, Math.min(token.length(), 10)) + "...");
                }
            } catch (Exception e) {
                System.out.println("Token validation error: " + e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token: " + e.getMessage());
                return;
            }
        } else {
            System.out.println("No token found in Authorization header");
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}