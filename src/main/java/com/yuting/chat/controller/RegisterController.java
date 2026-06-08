package com.yuting.chat.controller;

import com.yuting.chat.dto.RegisterRequest;
import com.yuting.chat.entity.User;
import com.yuting.chat.mapper.UserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RegisterController {

    private final UserMapper      userMapper;
    private final PasswordEncoder passwordEncoder;

    public RegisterController(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request){
        //输入校验
        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        if (username.length() > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名最长 50 字符"));
        }
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码长度不能少于 6 位"));
        }

        //检查 username 是否已经被注册
        if (userMapper.findByUsername(username) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名 \"" + username + "\" 已被使用"));
        }

        //加密密码,加盐
        String passwordHash = passwordEncoder.encode(password);

        //构造 User 对象并插入
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        userMapper.insert(user);

        //返回成功响应
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername()
                                       ));
    }


}
