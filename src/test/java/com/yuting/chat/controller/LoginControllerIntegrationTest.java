package com.yuting.chat.controller;

import com.yuting.chat.entity.User;
import com.yuting.chat.mapper.UserMapper;
import com.yuting.chat.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginControllerIntegrationTest {
    private MockMvc mockMvc;
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        //手动 mock 三个依赖
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        
        //手动 new Controller
        LoginController loginController = new LoginController(jwtService, userMapper, passwordEncoder);

        //手动搭建MockMvc,只挂这一个 Controller
        //不启动 Spring,不扫描 Mapper,不需要数据库
        mockMvc = MockMvcBuilders.standaloneSetup(loginController).build();
    }
    
    @Test
    void login_用户不存在应该返回401() throws Exception {
        when(userMapper.findByUsername(anyString())).thenReturn(null);

        mockMvc.perform(post("/api/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\": \"不存在的用户\",\"password\": \"123456\"}"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void login_密码错误应该返回401() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("小明");
        user.setPasswordHash("假的-hash");

        when(userMapper.findByUsername(anyString())).thenReturn(user);
        when(passwordEncoder.matches(anyString(),anyString())).thenReturn(false);

        mockMvc.perform(post("/api/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\": \"小明\",\"password\": \"wrongPass123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_登录成功应该返回200和token() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("小明");
        user.setPasswordHash("假的-hash");

        when(userMapper.findByUsername(anyString())).thenReturn(user);
        when(passwordEncoder.matches(anyString(),anyString())).thenReturn(true);
        when(jwtService.generateToken(1L,"小明")).thenReturn("fake-token-for-test");

        mockMvc.perform(post("/api/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"小明\", \"password\":\"correctPassword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value(user.getUsername()))
                .andExpect(jsonPath("$.userId").value(user.getId()));
    }

    @Test
    void login_用户名为空应该返回400()  throws Exception {
        mockMvc.perform(post("/api/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"\", \"password\":\"anyPassword\"}"))
               .andExpect(status().isBadRequest());
    }
}
