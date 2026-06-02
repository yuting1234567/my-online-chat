package com.yuting.chat.handler;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatHandler extends TextWebSocketHandler {

    // 所有在线的 session，线程安全，适合读多写少的广播场景
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    //JSON 序列化_Jackson
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("新连接进来：" + session.getId() + ", 当前在线：" + sessions.size());

        //广播最新在线人数给所有人
        broadcastOnlineCount();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        // 取出 payload, 消息的实际内容
        String payload = message.getPayload();
        System.out.println("收到消息：" +  payload + ", 来自 " + session.getId());

        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("type", "chat");
        chatMsg.put("content", payload);

        broadcast(chatMsg);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("连接断开：" + session.getId() + ", 当前在线：" + sessions.size());

        //广播最新在线人数给所有人
        broadcastOnlineCount();
    }

    /**
     * 向所有在线 session 广播一条结构化消息(自动序列化为 JSON)。
     * @param message 要广播的消息(键值对,会被序列化成 JSON 字符串)
     */
    private void broadcast(Map<String, Object> message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        }catch (Exception e){
            System.out.println("消息序列化失败：" + e.getMessage());
            return;
        }

        // 广播给sessions中的所有 session（包括自己）
        for (WebSocketSession s : sessions) {
            try {
                s.sendMessage(new TextMessage(json));
            }catch (Exception e){
                System.out.println("发送给 " + s.getId() + " 失败：" + e.getMessage());
            }
        }
    }

    private void broadcastOnlineCount(){
        Map<String,Object> onlineMsg = new HashMap<>();
        onlineMsg.put("type", "online");
        onlineMsg.put("count", sessions.size());

        broadcast(onlineMsg);

    }
}
