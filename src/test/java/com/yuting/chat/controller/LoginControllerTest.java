package com.yuting.chat.controller;

import com.yuting.chat.dto.LoginRequest;
import com.yuting.chat.entity.User;
import com.yuting.chat.mapper.UserMapper;
import com.yuting.chat.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginControllerTest {

    @Test
    void login_用户不存在应该返回401(){
        //Arrange:Mock 三个依赖
        JwtService jwtService = mock(JwtService.class);
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        //预设行为：无论传什么 username，findByUsername都返回 null
        when(userMapper.findByUsername(anyString())).thenReturn(null);

        LoginController loginController = new LoginController(jwtService, userMapper, passwordEncoder);

        LoginRequest request = new LoginRequest();
        request.setUsername("不存在的用户");
        request.setPassword("任何密码");

        ResponseEntity<?> response = loginController.login(request);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void login_密码错误应该返回401(){
        JwtService jwtService = mock(JwtService.class);
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("小明");
        user.setPasswordHash("123456");

        when(userMapper.findByUsername(anyString())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        LoginController loginController = new LoginController(jwtService, userMapper, passwordEncoder);

        LoginRequest request = new LoginRequest();
        request.setUsername("小明");
        request.setPassword("123456");

        ResponseEntity<?> response = loginController.login(request);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void login_登录成功应该返回200(){
        JwtService jwtService = mock(JwtService.class);
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("小明");
        user.setPasswordHash("123456");

        when(userMapper.findByUsername(anyString())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken(1L, "小明")).thenReturn("fake-token-for-test");

        LoginController loginController = new LoginController(jwtService, userMapper, passwordEncoder);

        LoginRequest request = new LoginRequest();
        request.setUsername("小明");
        request.setPassword("123456");

        ResponseEntity<?> response = loginController.login(request);

        assertEquals(200, response.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertNotNull(body);
        assertEquals("fake-token-for-test", body.get("token"));
        assertEquals(1L, body.get("userId"));
        assertEquals("小明", body.get("username"));
    }

    @Test
    void login_用户名为空应该返回400(){
        JwtService jwtService = mock(JwtService.class);
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        LoginController loginController = new LoginController(jwtService, userMapper, passwordEncoder);

        LoginRequest request = new LoginRequest();

        ResponseEntity<?> response = loginController.login(request);

        assertEquals(400, response.getStatusCode().value());
    }
}
