package crm.dopaflow_backend.Security;

import crm.dopaflow_backend.Service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public endpoints
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/users/all",
                                "/api/users/suspend-self",
                                "/api/profile/upload-photo",
                                "/api/profile/set-avatar",
                                "/static/**",
                                "/photos/**",
                                "/attachments/**",
                                "/contact-photos/**",
                                "/company-photos/**",
                                "/avatars/**",
                                "/ws/**"
                        ).permitAll()

                        // Authenticated endpoints
                        .requestMatchers(
                                "/api/auth/2fa/**",
                                "/api/contacts/**",
                                "/api/users/**",
                                "/api/opportunities/**",
                                "/api/tasks/**",
                                "/api/support/**",
                                "/api/companies/**",
                                "/api/reporting/**"
                        ).authenticated()

                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/photos/**")
                        .addResourceLocations("file:uploads/photos/")
                        .setCachePeriod(0);
                registry.addResourceHandler("/contact-photos/**")
                        .addResourceLocations("file:uploads/contact-photos/")
                        .setCachePeriod(0);
                registry.addResourceHandler("/company-photos/**")
                        .addResourceLocations("file:uploads/company-photos/")
                        .setCachePeriod(0);
                registry.addResourceHandler("/attachments/**")
                        .addResourceLocations("file:uploads/attachments/")
                        .setCachePeriod(0);
                registry.addResourceHandler("/avatars/**")
                        .addResourceLocations("file:uploads/avatars/")
                        .setCachePeriod(0);
            }
        };
    }
}
