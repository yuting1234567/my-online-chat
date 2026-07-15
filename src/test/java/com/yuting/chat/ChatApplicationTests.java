package com.yuting.chat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("需要完整环境(数据库 + JWT_SECRET),本地开发时手动启动应用验证即可")
class ChatApplicationTests {

    @Test
    void contextLoads() {
    }

}
