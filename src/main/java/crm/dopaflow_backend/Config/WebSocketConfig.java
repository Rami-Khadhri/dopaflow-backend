package crm.dopaflow_backend.Config;

import crm.dopaflow_backend.Service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketConfigurer , WebSocketMessageBrokerConfigurer {

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


    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins("*").withSockJS();
    }
}