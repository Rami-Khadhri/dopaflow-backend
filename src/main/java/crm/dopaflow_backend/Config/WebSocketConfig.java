package crm.dopaflow_backend.Config;

import crm.dopaflow_backend.Service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UserService userService;

    public WebSocketConfig(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userStatusWebSocketHandler(), "/ws/user-status")
                .setAllowedOrigins("*");
    }

    @Bean
    public UserStatusWebSocketHandler userStatusWebSocketHandler() {
        return new UserStatusWebSocketHandler(userService);
    }
}