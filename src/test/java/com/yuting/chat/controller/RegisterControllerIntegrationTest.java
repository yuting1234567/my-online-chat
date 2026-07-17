package com.yuting.chat.controller;

import com.yuting.chat.entity.User;
import com.yuting.chat.mapper.UserMapper;
import com.yuting.chat.service.UserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisterControllerIntegrationTest {
    private MockMvc    mockMvc;
    private UserMapper      userMapper;
    private PasswordEncoder passwordEncoder;
    private UserRegistry    userRegistry;

    @BeforeEach
    void setUp(){
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userRegistry = mock(UserRegistry.class);

        RegisterController registerController = new RegisterController(userMapper, passwordEncoder, userRegistry);

        mockMvc = MockMvcBuilders.standaloneSetup(registerController).build();
    }

    @Test
    void register_用户名为空应该返回400() throws Exception{
        mockMvc.perform(post("/api/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"\", \"password\":\"anyPassword\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_密码太短应该返回400() throws Exception{
        mockMvc.perform(post("/api/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"小明\", \"password\":\"Pass\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_用户名已经存在应该返回400() throws Exception{
        User user = new User();
        user.setId(1L);
        user.setUsername("小明");
        user.setPasswordHash("假的-hash");

        when(userMapper.findByUsername(anyString())).thenReturn(user);

        mockMvc.perform(post("/api/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"小明\", \"password\":\"anyPassword\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_注册成功应该返回200() throws Exception{
        when(userMapper.findByUsername(anyString())).thenReturn(null);

        doAnswer(invocation -> {
            User userArg = (User) invocation.getArguments()[0];
            userArg.setId(1L);
            return null;
        }).when(userMapper).insert(any(User.class));

        mockMvc.perform(post("/api/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"小明\", \"password\":\"anyPassword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.username").value("小明"));
    }
}