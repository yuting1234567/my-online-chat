package com.yuting.chat.service;

import com.yuting.chat.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserRegistryTest {

    @Test
    void init_加载完成后应该能通过exists查到用户() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.findAllUsernames()).thenReturn(List.of("小红", "晶晶", "小明"));

        UserRegistry userRegistry = new UserRegistry(userMapper);
        userRegistry.init();

        assertTrue(userRegistry.exists("小红"));
        assertTrue(userRegistry.exists("晶晶"));
        assertTrue(userRegistry.exists("小明"));
    }

    @Test
    void exists_对不存在的用户返回false() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.findAllUsernames()).thenReturn(List.of("小红"));

        UserRegistry userRegistry = new UserRegistry(userMapper);
        userRegistry.init();

        assertFalse(userRegistry.exists("其他人"));
    }

    @Test
    void register_能增量添加用户到缓存() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.findAllUsernames()).thenReturn(List.of());

        UserRegistry userRegistry = new UserRegistry(userMapper);
        userRegistry.init();

        assertFalse(userRegistry.exists("晶晶"));
        userRegistry.register("晶晶");
        assertTrue(userRegistry.exists("晶晶"));
    }
}
