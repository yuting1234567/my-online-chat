package com.yuting.chat.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuting.chat.entity.Message;
import com.yuting.chat.mapper.MessageMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ChatHandler extends TextWebSocketHandler {

    // 所有在线的 session，线程安全，适合读多写少的广播场景
    private final Set<WebSocketSession> sessions     = new CopyOnWriteArraySet<>();
    //JSON 序列化_Jackson
    private final ObjectMapper objectMapper;
    //MyBatis Mapper,Spring 自动注入
    private final MessageMapper messageMapper;
    //构造器注入：Spring 创建 ChatHandler 时会传入 MessageMapper、objectMapper
    public ChatHandler(MessageMapper messageMapper, ObjectMapper objectMapper) {
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);

        String username = (String) session.getAttributes().get("username");
        System.out.println("新连接进来：" + session.getId() + ", 用户：" + username + " , 当前在线：" + sessions.size());

        sendHistory(session);

        broadcastSystemMessage(username + " 加入了聊天室");
        
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

        //先存到数据库（持久化）
        Message message = new Message();
        message.setUsername(username);
        message.setContent(content);
        try{
            messageMapper.insertMessage(message);
            //插入后，message.getId()已经被 MyBatis 自动回填
        }catch (Exception e){
            System.out.println("消息存盘失败：" + e);
            e.printStackTrace();
            //注意：即使存盘失败，我们还是继续广播——保证用户体验，日志记录失败
        }

        //广播给所有人
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

    private void sendHistory(WebSocketSession session){
        try{
            //查最近 50条（数据库按 id DESC 倒序返回）
            List<Message> recent = messageMapper.findRecent(50);

            //反转（老的消息在前，新的在后），方便前端按时间顺序展示
            Collections.reverse(recent);

            Map<String, Object> historyMSg = new HashMap<>();
            historyMSg.put("type", "history");
            historyMSg.put("messages", recent);

            //序列化为 JSON,单独发给这个 session
            String json = objectMapper.writeValueAsString(historyMSg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            System.out.println("推送历史消息失败：" + e);
            e.printStackTrace();
        }
    }
}
