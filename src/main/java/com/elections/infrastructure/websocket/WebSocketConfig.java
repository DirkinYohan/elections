package com.elections.infrastructure.websocket;

import com.elections.infrastructure.metrics.MetricsService;
import com.elections.infrastructure.web.WebConfig;
import com.elections.modules.identity.IdentityService;
import com.elections.modules.juries.JurySocketHandler;
import com.elections.modules.verification.VerificationSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket puro (sin STOMP ni SockJS). El navegador no permite cabeceras propias en el handshake,
 * así que el token viaja como ?token=. Un token inválido se acepta y se cierra con 4401 para que el
 * cliente sepa que no debe reintentar.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VerificationSocketHandler verification;
    private final JurySocketHandler juries;
    private final IdentityService identity;
    private final String[] origins;

    public WebSocketConfig(VerificationSocketHandler verification, JurySocketHandler juries, IdentityService identity,
                           MetricsService metrics, @Value("${app.cors-origins:*}") String origins) {
        this.verification = verification;
        this.juries = juries;
        this.identity = identity;
        this.origins = WebConfig.parseOrigins(origins);
        metrics.bindWs(() -> verification.connections() + juries.connections());
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        HandshakeInterceptor auth = new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {
                String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
                try {
                    identity.session(token).ifPresent(s -> attributes.put("session", s));
                } catch (RuntimeException ignored) {
                    // Redis caído: se cierra con 4401 y el cliente lo muestra
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {
            }
        };
        registry.addHandler(verification, "/ws/verification").addInterceptors(auth).setAllowedOriginPatterns(origins);
        registry.addHandler(juries, "/ws/juries").addInterceptors(auth).setAllowedOriginPatterns(origins);
    }
}
