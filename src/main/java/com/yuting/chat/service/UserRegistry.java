package com.yuting.chat.service;

import com.yuting.chat.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class UserRegistry {

    private final UserMapper userMapper;
    private final Set<String> usernames = ConcurrentHashMap.newKeySet();

    public UserRegistry(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @PostConstruct
    public void init() {
        List<String> all = userMapper.findAllUsernames();
        usernames.addAll(all);
        log.info("UserRegistry 初始化完成，加载用户数：{}", usernames.size());
    }

    public boolean exists(String username) {
        return usernames.contains(username);
    }

    public void register(String username) {
        usernames.add(username);
        log.info("新用户加入缓存: {}", username);
    }
}
