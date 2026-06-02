package com.yuting.chat.handler;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.type.TypeReference;
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
        System.out.println("新连接进来：" + session.getId() + " , 当前在线：" + sessions.size());

        //广播最新在线人数给所有人
        broadcastOnlineCount();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        // 取出 payload, 消息的实际内容
        String payload = message.getPayload();

        //解析客户端发来的 JSON
        Map<String, Object> msg;
        try {
            msg = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        }catch (Exception e){
            System.out.println("消息解析失败，忽略：" + payload);
            return;
        }

        //根据 type,处理不同逻辑
        String type = (String) msg.get("type");
        if(type == null) {
            System.out.println("消息缺少 type 字段，忽略：" + payload);
            return;
        }

        switch (type) {
            case "join":
                handleJoin(session, msg);
                break;
            case "chat" :
                handleChat(session, msg);
                break;
            default:
                System.out.println("未知消息类型：" + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);

        String username = (String) session.getAttributes().get("username");
        System.out.println("连接断开：" + session.getId() +  " (" + username + "), 当前在线：" + sessions.size());

        if(username != null) {
            broadcastSystemMessage(username + " 离开了聊天室");
        }

        //广播最新在线人数给所有人
        broadcastOnlineCount();
    }

    /**
     * 处理用户加入：存用户名到 session属性，广播加入提示。
     */
    private void handleJoin(WebSocketSession session, Map<String, Object> msg) {
        String username = (String) msg.get("username");
        if(username == null || username.isBlank()) {
            System.out.println("join 消息缺少有效 username,忽略");
            return;
        }

        //把用户名挂在 session 上，下次前端不用传，后端从 session 取
        session.getAttributes().put("username", username);
        String content = username + " 加入了聊天室";
        System.out.println(content + "(session: " + session.getId() + ")");

        broadcastSystemMessage(content);
    }

    /**
     * 处理聊天消息:取出发送者用户名,广播带 username 的 chat 消息。
     */
    private void handleChat(WebSocketSession session, Map<String, Object> msg) {
        String username = (String) session.getAttributes().get("username");
        if(username == null) {
            System.out.println("用户未加入(无 username)，拒绝发送");
            return;
        }

        String content = (String) msg.get("content");
        if(content == null || content.isBlank()) {
            return;
        }

        System.out.println("收到 [" + username + "]: " + content);

        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("type", "chat");
        chatMsg.put("username", username);
        chatMsg.put("content", content);

        broadcast(chatMsg);
    }

    /**
     * 广播当前在线人数。
     */
    private void broadcastOnlineCount(){
        Map<String,Object> onlineMsg = new HashMap<>();
        onlineMsg.put("type", "online");
        onlineMsg.put("count", sessions.size());

        broadcast(onlineMsg);
    }

    /**
     * 广播一条系统消息
     */
    private void broadcastSystemMessage(String content){
        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("type", "system");
        sysMsg.put("content", content);
        broadcast(sysMsg);
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
}
