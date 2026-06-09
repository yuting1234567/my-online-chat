package com.yuting.chat.config;

import com.yuting.chat.service.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes)
            throws Exception {
        //从 url 取出 token
        String token = UriComponentsBuilder.fromUri(request.getURI())
                                                   .build()
                                                   .getQueryParams()
                                                   .getFirst("token");

        if (token == null || token.isBlank()) {
            System.out.println("websocket 握手拒绝：缺少 token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try{
            Claims claims = jwtService.parseToken(token);
            String username = claims.getSubject();
            Long userId = claims.get("userId", Long.class);

            attributes.put("username", username);
            attributes.put("userId", userId);

            System.out.println("WebSocket 握手通过：" + username + "(userId=" + userId +  ")");
            return true;
        }catch (Exception e){
            //过期、签名不对、格式错误
            System.out.println("WebSocket 握手拒绝：token 校验失败 - " + e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {

    }
}
