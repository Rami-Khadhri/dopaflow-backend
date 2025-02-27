package crm.dopaflow_backend.Security;

import crm.dopaflow_backend.Service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtUtil jwtUtil;
    private final ObjectProvider<UserService> userServiceProvider;

    public SecurityConfig(JwtUtil jwtUtil, ObjectProvider<UserService> userServiceProvider) {
        this.jwtUtil = jwtUtil;
        this.userServiceProvider = userServiceProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtUtil, userServiceProvider);

        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless API
                .cors(cors -> cors.configure(http)) // Enable CORS
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/users/**",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/profile/upload-photo",
                                "/api/profile/set-avatar",
                                "/static/**",
                                "/photos/**", // Serve uploaded photos
                                "/contact-photos/**", // Serve uploaded photos
                                "/avatars/**", // Serve static avatars
                                "/media/**"
                        ).permitAll()
                        // Authenticated endpoints
                        .requestMatchers("/api/auth/2fa/**",
                                "/api/contacts/**",
                                "/api/users/**"
                        ).authenticated()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // Map /photos/ to file:uploads/photos/
                registry.addResourceHandler("/photos/**")
                        .addResourceLocations("file:uploads/photos/")
                        .setCachePeriod(0); // Disable caching for development

                registry.addResourceHandler("/contact-photos/**")
                        .addResourceLocations("file:uploads/contact-photos/")
                        .setCachePeriod(0); // Disable caching for development

                // Map /avatars/ to file:uploads/avatars/
                registry.addResourceHandler("/avatars/**")
                        .addResourceLocations("file:uploads/avatars/")
                        .setCachePeriod(0); // Disable caching for development
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}