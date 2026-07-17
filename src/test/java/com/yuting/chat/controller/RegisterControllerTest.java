package com.yuting.chat.controller;

import com.yuting.chat.dto.RegisterRequest;
import com.yuting.chat.entity.User;
import com.yuting.chat.mapper.UserMapper;
import com.yuting.chat.service.UserRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterControllerTest {

    @Test
    void register_用户名为空应该返回400() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        //只有密码
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setPassword("123456");

        RegisterController registerController = new RegisterController(userMapper, passwordEncoder, userRegistry);
        ResponseEntity<?> register = registerController.register(registerRequest);

        assertEquals(400, register.getStatusCode().value());
    }

    @Test
    void register_密码太短应该返回400() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("小明");
        registerRequest.setPassword("123");

        RegisterController registerController = new RegisterController(userMapper, passwordEncoder, userRegistry);
        ResponseEntity<?> register = registerController.register(registerRequest);

        assertEquals(400, register.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) register.getBody();

        assertNotNull(body);
        assertTrue(body.get("error").toString().contains("密码长度"));
        verify(userMapper, never()).insert(any());
    }

    @Test
    void register_用户名已经存在应该返回400() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        User user = new User();
        user.setUsername("小明");
        user.setPasswordHash("123456");

        when(userMapper.findByUsername(anyString())).thenReturn(user);

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("小明");
        registerRequest.setPassword("123456");

        RegisterController registerController = new RegisterController(userMapper, passwordEncoder, userRegistry);
        ResponseEntity<?> register = registerController.register(registerRequest);

        assertEquals(400, register.getStatusCode().value());
    }

    @Test
    void register_注册成功应该返回200() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserRegistry userRegistry = mock(UserRegistry.class);

        when(userMapper.findByUsername(anyString())).thenReturn(null);
        doAnswer(invocation -> {
            User userArg = invocation.getArgument(0);
            userArg.setId(1L);
            return null;
        }).when(userMapper).insert(any(User.class));

        when(passwordEncoder.encode(anyString())).thenReturn("假-123456");

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("小明");
        registerRequest.setPassword("123456");

        RegisterController registerController = new RegisterController(userMapper, passwordEncoder, userRegistry);
        ResponseEntity<?> register = registerController.register(registerRequest);

        assertEquals(200, register.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) register.getBody();
        assertNotNull(body);
        assertEquals(1L, body.get("id"));
        assertEquals("小明", body.get("username"));

        verify(userRegistry).register("小明");
    }
}
