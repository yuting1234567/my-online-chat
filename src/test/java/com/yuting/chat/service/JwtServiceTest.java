package com.yuting.chat.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void generateToken_应该返回合法的JWT字符串() {
        //Arrange:准备
        String secret = "this-is-a-test-secret-key-for-testing-only-must-be-long-enough-for-hmac-sha";
        long expirationHours = 24;
        JwtService jwtService = new JwtService(secret,expirationHours);

        Long userId = 1L;
        String username = "小明";

        //Act：执行
        String token = jwtService.generateToken(userId,username);

        //Assert:断言
        assertNotNull(token);
        assertEquals(2,token.chars().filter(ch -> ch == '.').count());
    }

    @Test
    void parseToken_应该能解析出原userId和username(){
        String secret = "this-is-a-test-secret-key-for-testing-only-must-be-long-enough-for-hmac-sha";
        long expirationHours = 24;
        JwtService jwtService = new JwtService(secret,expirationHours);

        Long userId = 1L;
        String username = "小明";
        String token = jwtService.generateToken(userId,username);

        Claims parseToken = jwtService.parseToken(token);

        assertEquals(username,parseToken.getSubject());
        assertEquals(userId,parseToken.get("userId", Long.class));
    }

    @Test
    void parseToken_过期token应该抛ExpiredJwtException(){
        String secret = "this-is-a-test-secret-key-for-testing-only-must-be-long-enough-for-hmac-sha";
        JwtService jwtService = new JwtService(secret,-1);

        String token = jwtService.generateToken(1L,"小明");

        assertThrows(ExpiredJwtException.class,() -> jwtService.parseToken(token));
    }

    @Test
    void parseToken_篡改的token应该抛异常(){
        String secret = "this-is-a-test-secret-key-for-testing-only-must-be-long-enough-for-hmac-sha";
        JwtService jwtService = new JwtService(secret,24);

        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0IiwidXNlcm5hbWUiOiJ0ZXN0IiwiaWF0IjoxNzAwMDAwMDAwfQ.dummy_signature_not_real";

        assertThrows(SignatureException.class, () -> jwtService.parseToken(token));
    }
}
