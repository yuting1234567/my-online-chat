package com.yuting.chat.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuting.chat.entity.Message;
import com.yuting.chat.mapper.MessageMapper;
import com.yuting.chat.service.UserRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class ChatHandler extends TextWebSocketHandler {

    // 单条消息最大长度，超过则拒绝。防止超大 payload 撑爆 DB 和广播带宽
    private static final int MAX_CONTENT_LENGTH = 1000;

    // 所有在线的 session，线程安全，适合读多写少的广播场景
    private final Set<WebSocketSession> sessions     = new CopyOnWriteArraySet<>();
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> userSessionMap = new ConcurrentHashMap<>();

    //JSON 序列化_Jackson
    private final ObjectMapper objectMapper;
    //MyBatis Mapper,Spring 自动注入
    private final MessageMapper messageMapper;

    private final UserRegistry userRegistry;

    //构造器注入：Spring 创建 ChatHandler 时会传入 MessageMapper、objectMapper
    public ChatHandler(MessageMapper messageMapper, ObjectMapper objectMapper,  UserRegistry userRegistry) {
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
        this.userRegistry = userRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            log.error("严重异常：username 为 null，可能拦截器未生效，sessionId={}",session.getId());
            session.close();
            return;
        }

        WebSocketSessionDecorator concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session,
                5000,
                64 * 1024
        );

        sessionMap.put(session.getId(), concurrentSession);
        sessions.add(concurrentSession);
        userSessionMap.put(username, concurrentSession);

        log.info("新连接进来：{}, 用户：{}, 当前在线：{}", session.getId(), username, sessions.size());

        sendHistory(concurrentSession);

        sendUndeliveredPrivate(concurrentSession, username);

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
            log.warn("消息解析失败，忽略 payload={}", payload, e);
            return;
        }

        //根据 type,处理不同逻辑
        String type = (String) msg.get("type");
        if(type == null) {
            log.warn("消息缺少 type 字段，忽略：{}", payload);
            return;
        }

        WebSocketSession concurrent = sessionMap.get(session.getId());
        if (concurrent == null) {
            log.error("严重异常：sessionMap 中找不到装饰器, sessionId={}", session.getId());
            return;
        }

        switch (type) {
            case "chat" :
                handleChat(concurrent, msg);
                break;
            case "private" :
                handlePrivate(concurrent, msg);
                break;
            default:
                log.warn("未知消息类型：{}, sessionId={}", type, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        WebSocketSession concurrent = sessionMap.remove(session.getId());
        if (concurrent != null) {
            sessions.remove(concurrent);
        }

        String username = (String) session.getAttributes().get("username");
        log.info("连接断开：{}, ({}), 当前在线：{}", session.getId(), username, sessions.size());

        if(username != null) {
            userSessionMap.remove(username);
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
            log.error("严重异常:handleChat 中 username 为 null,可能拦截器配置错误, sessionId={}", session.getId());
            return;
        }

        String content = (String) msg.get("content");
        if(content == null || content.isBlank()) {
            log.debug("空消息，忽略，username={}", username);
            return;
        }

        //长度校验：超长直接拒绝，并单独告知发送者，不入库也不广播
        if(content.length() > MAX_CONTENT_LENGTH) {
            log.warn("消息超长被拒绝，username={}, length={}", username, content.length());
            sendError(session, "消息过长，最多 " + MAX_CONTENT_LENGTH + " 字");
            return;
        }

        log.debug("收到【{}】: {}", username, content);

        //先存到数据库（持久化）
        Message message = new Message();
        message.setUsername(username);
        message.setContent(content);
        try{
            messageMapper.insertMessage(message);
            //插入后，message.getId()已经被 MyBatis 自动回填
        }catch (Exception e){
            log.error("消息存盘失败", e);
            //注意：即使存盘失败，我们还是继续广播——保证用户体验，日志记录失败
        }

        //广播给所有人
        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("type", "chat");
        chatMsg.put("username", username);
        chatMsg.put("content", content);

        broadcast(chatMsg);
    }

    private void handlePrivate(WebSocketSession session, Map<String, Object> msg) {
        //from 从 session attributes 取(永远不信任客户端)
        String from = (String) session.getAttributes().get("username");
        if(from == null) {
            log.error("严重异常：handlePrivate 中 from 为 null，sessionId={}", session.getId());
            return;
        }

        String to = (String) msg.get("to");
        if(to == null || to.isBlank()) {
            log.warn("私聊缺少 to 字段，忽略，from={}", from);
            return;
        }

        //长度校验：超长直接拒绝，并单独告知发送者，不入库也不广播
        String content = (String) msg.get("content");
        if(content == null || content.isBlank()) {
            log.debug("空私聊消息，忽略，from={}", from);
            return;
        }
        if(content.length() > MAX_CONTENT_LENGTH) {
            log.warn("私聊超长,from={},length={}", from, content.length());
            sendError(session, "消息过长，最多 " + MAX_CONTENT_LENGTH + " 字");
            return;
        }

        //接收者存在性校验（用 UserRegistry, 不查 DB）
        if(!userRegistry.exists(to)) {
            log.warn("私聊接收者不存在，from={}, to={}", from, to);
            sendError(session, "该用户不存在：" + to);
            return;
        }

        //存到数据库（持久化）
        Message message = new Message();
        message.setUsername(from);
        message.setToUsername(to);
        message.setContent(content);
        try{
            messageMapper.insertMessage(message);
        }catch (Exception e){
            log.error("私聊消息存盘失败，from={}, to={}", from, to, e);
            sendError(session, "消息发送失败，请稍后重试");
            return;
        }

        if(from.equals(to)) {
            log.debug("私聊（自发自收），只持久化，from={}", from);
            messageMapper.markDelivered(message.getId());
            return;
        }

        WebSocketSession recipientSession = userSessionMap.get(to);
        if(recipientSession == null) {
            log.debug("接收者离线，消息已存 DB 等重连补推，from={}, to={}", from, to);
            return;
        }

        //推送消息
        Map<String, Object> privateMsg = new HashMap<>();
        privateMsg.put("type", "private");
        privateMsg.put("id", message.getId());
        privateMsg.put("from", from);
        privateMsg.put("content", content);
        try{
            recipientSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(privateMsg)));
            messageMapper.markDelivered(message.getId());
        }catch (Exception e){
            log.error("私聊推送失败，from={}, to={}", from, to, e);
        }
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
     * 给单个 session 发一条错误提示（只发给当事人，不广播）。
     */
    private void sendError(WebSocketSession session, String content){
        Map<String, Object> errMsg = new HashMap<>();
        errMsg.put("type", "error");
        errMsg.put("content", content);
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errMsg)));
        } catch (Exception e) {
            log.warn("发送错误提示失败，sessionId={}", session.getId(), e);
        }
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
            log.error("消息序列化失败", e);
            return;
        }

        // 广播给sessions中的所有 session（包括自己）
        for (WebSocketSession s : sessions) {
            try {
                s.sendMessage(new TextMessage(json));
            }catch (Exception e){
                log.warn("发送给：{} 的消息失败", s.getId(), e);
            }
        }
    }

    private void sendHistory(WebSocketSession session){
        try{
            //查最近 50条（数据库按 id DESC 倒序返回）
            List<Message> recent = messageMapper.findRecentGroup(50);

            //反转（老的消息在前，新的在后），方便前端按时间顺序展示
            Collections.reverse(recent);

            Map<String, Object> historyMSg = new HashMap<>();
            historyMSg.put("type", "history");
            historyMSg.put("messages", recent);

            //序列化为 JSON,单独发给这个 session
            String json = objectMapper.writeValueAsString(historyMSg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("推送历史消息失败", e);
        }
    }

    private void sendUndeliveredPrivate(WebSocketSession session, String toUsername){
        List<Message> undelivered = messageMapper.findUndeliveredPrivate(toUsername);
        if(undelivered.isEmpty()){
            return;
        }
        log.info("重连补推私聊消息：username={}, count={}", toUsername, undelivered.size());

        for(Message msg : undelivered){
            Map<String, Object> privateMsg = new HashMap<>();
            privateMsg.put("type", "private");
            privateMsg.put("id", msg.getId());
            privateMsg.put("from", msg.getUsername());
            privateMsg.put("content", msg.getContent());
            privateMsg.put("createdAt", msg.getCreatedAt().toString());
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(privateMsg)));
                messageMapper.markDelivered(msg.getId());
            }catch (Exception e){
                log.error("补推私聊失败，id={}, from={}, to={}", msg.getId(), msg.getUsername(), toUsername, e);
            }
        }
    }
}
