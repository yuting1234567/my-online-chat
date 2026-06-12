package com.yuting.chat.controller;

import com.yuting.chat.dto.LoginRequest;
import com.yuting.chat.entity.User;
import com.yuting.chat.mapper.UserMapper;
import com.yuting.chat.service.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class LoginController {

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public LoginController(JwtService jwtService, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request){
        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.isBlank() || password == null) {
            log.warn("登录请求参数不完整");
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        //查用户是否存在
        User user = userMapper.findByUsername(username);
        if(user == null){
            log.warn("登录失败，用户不存在，username={}", username);
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        //检查密码是否正确
        if(!passwordEncoder.matches(password, user.getPasswordHash())){
            log.warn("登录失败，密码错误，username={}", username);
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        //签发 token
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        log.info("用户登录成功：username={}, userId={}", username, user.getId());

        //返回
        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", user.getUsername(),
                "userId" ,user.getId()
                                       ));
    }
}
