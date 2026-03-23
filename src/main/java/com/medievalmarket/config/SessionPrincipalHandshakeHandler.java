package com.medievalmarket.config;

import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.http.server.ServerHttpRequest;
import java.security.Principal;
import java.util.Map;

public class SessionPrincipalHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String sid = (String) attributes.get("sessionId");
        if (sid != null) return () -> sid;
        return super.determineUser(request, wsHandler, attributes);
    }
}
