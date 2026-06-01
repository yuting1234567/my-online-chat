package com.yuting.chat.handler;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatHandler extends TextWebSocketHandler {

    // 所有在线的 session，线程安全，适合读多写少的广播场景
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("新连接进来：" + session.getId() + ", 当前在线：" + sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        // 取出 payload, 消息的实际内容
        String payload = message.getPayload();
        System.out.println("收到消息：" +  payload + ", 来自 " + session.getId());

        // 广播给sessions中的所有 session（包括自己）
        for (WebSocketSession s : sessions) {
            try {
                s.sendMessage(new TextMessage(payload));
            }catch (Exception e){
                System.out.println("发送给 " + s.getId() + " 失败：" + e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("连接断开：" + session.getId() + ", 当前在线：" + sessions.size());
    }

}
