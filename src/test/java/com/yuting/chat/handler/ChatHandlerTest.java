package com.yuting.chat.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuting.chat.entity.Message;
import com.yuting.chat.mapper.MessageMapper;
import com.yuting.chat.service.UserRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHandlerTest {

    // ============ 辅助方法 ============

    /**
     * 造一个 mock WebSocketSession,填好 id 和 username attribute
     */
    private WebSocketSession mockSession(String username, String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("username", username);
        when(session.getAttributes()).thenReturn(attrs);
        return session;
    }

    /**
     * 创建 handler 并把 sessions 手动注入 sessionMap / userSessionMap
     * (跳过 afterConnectionEstablished 的复杂 setup)
     */
    @SuppressWarnings("unchecked")
    private ChatHandler newHandlerWithSessions(MessageMapper messageMapper, UserRegistry userRegistry, WebSocketSession ... sessions) {
        ChatHandler handler = new ChatHandler(messageMapper, new ObjectMapper(), userRegistry);
        Map<String, WebSocketSession> sessionMap = (Map<String, WebSocketSession>) ReflectionTestUtils.getField(handler, "sessionMap");
        Map<String, WebSocketSession> userSessionMap = (Map<String, WebSocketSession>) ReflectionTestUtils.getField(handler, "userSessionMap");
        for (WebSocketSession session : sessions) {
            sessionMap.put(session.getId(), session);
            String username = (String) session.getAttributes().get("username");
            userSessionMap.put(username, session);
        }
        return handler;
    }

    @Test
    void handlePrivate_接收者在线应该insert并推送和mark() throws Exception{
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        when(userRegistry.exists("Bob")).thenReturn(true);
        doAnswer(invocationOnMock -> {
            Message message = invocationOnMock.getArgument(0);
            message.setId(100L);
            return null;
        }).when(messageMapper).insertMessage(any(Message.class));

        WebSocketSession aliceSession = mockSession("Alice", "sid-alice");
        WebSocketSession bobSession = mockSession("Bob", "sid-bob");

        ChatHandler handler = newHandlerWithSessions(messageMapper, userRegistry, aliceSession, bobSession);

        String payload = "{\"type\":\"private\", \"to\":\"Bob\", \"content\": \"hi\"}";
        handler.handleTextMessage(aliceSession, new TextMessage(payload));

        //Message 插入正确
        ArgumentCaptor<Message> msgCap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insertMessage(msgCap.capture());
        Message inserted = msgCap.getValue();
        assertEquals("Alice", inserted.getUsername());
        assertEquals("Bob", inserted.getToUsername());
        assertEquals("hi", inserted.getContent());

        //Bob 收到消息
        ArgumentCaptor<TextMessage> sentCap = ArgumentCaptor.forClass(TextMessage.class);
        verify(bobSession).sendMessage(sentCap.capture());
        String sent = sentCap.getValue().getPayload();
        assertTrue(sent.contains("\"type\":\"private\""));
        assertTrue(sent.contains("\"from\":\"Alice\""));
        assertTrue(sent.contains("\"content\":\"hi\""));

        //mark delivered 被调
        verify(messageMapper).markDelivered(100L);
    }

    @Test
    void handlePrivate_接收者离线应该insert但不推送不mark() throws Exception{
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        when(userRegistry.exists("Bob")).thenReturn(true);
        doAnswer(invocationOnMock -> {
            Message message = invocationOnMock.getArgument(0);
            message.setId(100L);
            return null;
        }).when(messageMapper).insertMessage(any(Message.class));

        WebSocketSession aliceSession = mockSession("Alice", "sid-alice");

        ChatHandler handler = newHandlerWithSessions(messageMapper, userRegistry, aliceSession);

        String payload = "{\"type\":\"private\", \"to\":\"Bob\", \"content\": \"hi\"}";
        handler.handleTextMessage(aliceSession, new TextMessage(payload));

        //Message 插入正确
        ArgumentCaptor<Message> msgCap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insertMessage(msgCap.capture());
        Message inserted = msgCap.getValue();
        assertEquals("Alice", inserted.getUsername());
        assertEquals("Bob", inserted.getToUsername());
        assertEquals("hi", inserted.getContent());

        //没推送
        verify(aliceSession, never()).sendMessage(any(TextMessage.class));

        //没 mark
        verify(messageMapper, never()).markDelivered(anyLong());
    }

    @Test
    void handlePrivate_接收者不存在应该sendError不insert() throws Exception{
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserRegistry userRegistry = mock(UserRegistry.class);
        when(userRegistry.exists("不存在的人")).thenReturn(false);

        WebSocketSession aliceSession = mockSession("Alice", "sid-alice");
        ChatHandler handler = newHandlerWithSessions(messageMapper, userRegistry, aliceSession);

        String payload = "{\"type\": \"private\", \"to\": \"不存在的人\", \"content\": \"hi\"}";
        handler.handleTextMessage(aliceSession, new TextMessage(payload));

        //从来没 insert 过
        verify(messageMapper, never()).insertMessage(any(Message.class));

        ArgumentCaptor<TextMessage> cap = ArgumentCaptor.forClass(TextMessage.class);
        verify(aliceSession).sendMessage(cap.capture());
        String sent = cap.getValue().getPayload();
        assertTrue(sent.contains("\"type\":\"error\""));
        assertTrue(sent.contains("该用户不存在"));
    }

    @Test
    void handlePrivate_自发自收应该insert并立即mark不推送() throws Exception{
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        when(userRegistry.exists("Alice")).thenReturn(true);
        doAnswer(invocationOnMock -> {
            Message message = invocationOnMock.getArgument(0);
            message.setId(100L);
            return null;
        }).when(messageMapper).insertMessage(any(Message.class));

        WebSocketSession aliceSession = mockSession("Alice", "sid-alice");

        ChatHandler handler = newHandlerWithSessions(messageMapper, userRegistry, aliceSession);

        String payload = "{\"type\":\"private\", \"to\":\"Alice\", \"content\": \"hi\"}";
        handler.handleTextMessage(aliceSession, new TextMessage(payload));

        //Message 插入正确
        ArgumentCaptor<Message> msgCap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insertMessage(msgCap.capture());
        Message inserted = msgCap.getValue();
        assertEquals("Alice", inserted.getUsername());
        assertEquals("Alice", inserted.getToUsername());
        assertEquals("hi", inserted.getContent());

        //没推送
        verify(aliceSession, never()).sendMessage(any(TextMessage.class));

        //mark delivered 被调
        verify(messageMapper).markDelivered(100L);
    }

    @Test
    void handlePrivate_内容超长应该sendError不insert() throws Exception{
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        WebSocketSession aliceSession = mockSession("Alice", "sid-alice");

        ChatHandler handler = newHandlerWithSessions(messageMapper, userRegistry, aliceSession);

        String content = "a".repeat(1001);
        String payload = "{\"type\": \"private\", \"to\": \"Alice\", \"content\": \"" + content + "\"}";
        handler.handleTextMessage(aliceSession, new TextMessage(payload));

        //从来没 insert 过
        verify(messageMapper, never()).insertMessage(any(Message.class));

        ArgumentCaptor<TextMessage> cap = ArgumentCaptor.forClass(TextMessage.class);
        verify(aliceSession).sendMessage(cap.capture());
        String sent = cap.getValue().getPayload();
        assertTrue(sent.contains("\"type\":\"error\""));
        assertTrue(sent.contains("消息过长"));
    }

    @Test
    void sendUndeliveredPrivate_有未送达消息应该全部推送并mark() throws Exception{
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        //造两条未送达消息
        Message m1 = new Message();
        m1.setId(1L);
        m1.setUsername("Alice");
        m1.setToUsername("Bob");
        m1.setContent("hi 1Bob");
        m1.setDelivered(false);
        m1.setCreatedAt(java.time.LocalDateTime.now());

        Message m2 = new Message();
        m2.setId(2L);
        m2.setUsername("charlie");
        m2.setToUsername("Bob");
        m2.setContent("hi 2Bob");
        m2.setDelivered(false);
        m2.setCreatedAt(java.time.LocalDateTime.now());

        when(messageMapper.findUndeliveredPrivate("Bob")).thenReturn(List.of(m1, m2));

        WebSocketSession bobSession = mockSession("Bob", "sid-bob");
        ChatHandler handler = newHandlerWithSessions(messageMapper, userRegistry, bobSession);

        handler.sendUndeliveredPrivate(bobSession, "Bob");

        //两条都被推送
        verify(bobSession, times(2)).sendMessage(any(TextMessage.class));

        //两条都被 mark
        verify(messageMapper).markDelivered(1L);
        verify(messageMapper).markDelivered(2L);
    }

    @Test
    void sendUndeliveredPrivate_无未送达消息应该不推送不mark() throws Exception{
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        when(messageMapper.findUndeliveredPrivate("Bob")).thenReturn(List.of());

        WebSocketSession bobSession = mockSession("Bob", "sid-bob");
        ChatHandler handler = newHandlerWithSessions(messageMapper, userRegistry, bobSession);

        handler.sendUndeliveredPrivate(bobSession, "Bob");

        //不推送
        verify(bobSession, never()).sendMessage(any(TextMessage.class));

        //不 mark
        verify(messageMapper, never()).markDelivered(anyLong());
    }
}
