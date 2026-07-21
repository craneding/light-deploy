package com.lightdeploy.backend.config;

import com.lightdeploy.backend.websocket.DeployLogWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DeployLogWebSocketHandler deployLogWebSocketHandler;

    public WebSocketConfig(DeployLogWebSocketHandler deployLogWebSocketHandler) {
        this.deployLogWebSocketHandler = deployLogWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(deployLogWebSocketHandler, "/ws/deploy")
                .setAllowedOrigins("*");
    }
}
