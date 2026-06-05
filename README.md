# 实时聊天室 Chat

基于 Spring Boot + WebSocket + MySQL 的多人实时聊天系统。支持多用户实时通信、在线人数显示、历史消息回放。

## 功能特性

- 多人实时聊天,基于 WebSocket 全双工通信
- 实时在线人数显示
- 用户名身份(简单形式,后续扩展为 JWT 鉴权)
- 加入/离开提示
- 消息持久化到 MySQL,新用户加入时自动展示最近 50 条历史
- 消息时间显示(历史显示具体时间,新消息显示当前时间)

## 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.5.6, Java 17 |
| 实时通信 | spring-boot-starter-websocket |
| 数据持久化 | MyBatis 3.0.3 + MySQL 8.0 |
| 连接池 | HikariCP(Spring Boot 默认) |
| 工具 | Lombok, Jackson(JSR-310 时间序列化) |
| 前端 | 原生 HTML + JavaScript(WebSocket API) |
| 构建 | Maven |
| 版本控制 | Git, Conventional Commits |

## 项目结构

```
src/main/java/com/yuting/chat/
├── ChatApplication.java         # Spring Boot 启动类,注册 MapperScan 和 ObjectMapper Bean
├── config/
│   └── WebSocketConfig.java     # WebSocket 配置:注册 ChatHandler 到 /chat
├── handler/
│   └── ChatHandler.java         # WebSocket 消息处理(连接生命周期 + 消息分发)
├── entity/
│   └── Message.java             # 消息实体,对应 messages 表
└── mapper/
    └── MessageMapper.java       # MyBatis Mapper 接口

src/main/resources/
├── application.properties       # 数据库连接配置(密码用环境变量)
└── static/
    └── chat.html                # 前端页面
```

## 消息协议(基于 WebSocket 上的 JSON)

### 客户端 → 服务器

**加入聊天室**:
```json
{"type": "join", "username": "小明"}
```

**发送消息**:
```json
{"type": "chat", "content": "你好"}
```

### 服务器 → 客户端

**普通聊天消息**:
```json
{"type": "chat", "username": "小明", "content": "你好"}
```

**系统消息(加入/离开提示)**:
```json
{"type": "system", "content": "小明 加入了聊天室"}
```

**在线人数更新**:
```json
{"type": "online", "count": 3}
```

**历史消息(仅推给新加入的用户,不广播)**:
```json
{"type": "history", "messages": [{"id": 1, "username": "小明", "content": "你好", "createdAt": "2026-06-04T14:05:29"}]}
```

## 数据库设计

```sql
CREATE DATABASE chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chat_db;

CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**关键设计决策**:
- `utf8mb4` 字符集:支持完整 UTF-8 包括 emoji(MySQL 默认的 `utf8` 只支持 3 字节)
- `BIGINT` 而非 `INT`:防御性设计,消息累计量可能很大
- `TEXT` 而非 `VARCHAR`:消息内容长度不可预测,TEXT 更稳
- `DEFAULT CURRENT_TIMESTAMP`:数据库自动填时间,业务代码无需关心

## 设计亮点

- **消息协议带 type 字段**:扩展性强,新增消息类型不破坏旧客户端(开闭原则)
- **服务器不信任客户端身份**:chat 消息中 username 由服务端从 session 取,不由客户端传入,防止伪造
- **历史消息只单独推给加入者**:别人不需要重看,sendMessage 与 broadcast 分离
- **失败隔离**:广播消息循环中,单个 session 发送失败不影响其他用户
- **构造器注入**:Spring 推荐的 DI 方式,字段 final、依赖显式可见、易于单元测试
- **敏感配置环境变量化**:数据库密码通过 `${DB_PASSWORD}` 占位符传入,不写进配置文件

## 本地运行

### 环境要求
- JDK 17
- Maven 3.8+
- MySQL 8.0

### 步骤

**1. 安装并启动 MySQL**

Mac 推荐使用 Homebrew:
```bash
brew install mysql@8.0
brew services start mysql@8.0
mysql_secure_installation   # 设置 root 密码
```

**2. 创建数据库与表**

```sql
CREATE DATABASE chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chat_db;

CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**3. 配置数据库密码(通过环境变量)**

`application.properties` 中密码声明为 `${DB_PASSWORD}`,必须通过环境变量传入(不会写进任何文件):

- **IDEA 用户**:Run → Edit Configurations → Environment variables 添加 `DB_PASSWORD=你的密码`
- **命令行**:`DB_PASSWORD=你的密码 mvn spring-boot:run`
- **永久设置**:把 `export DB_PASSWORD=你的密码` 加到 `~/.zprofile` 或 `~/.bashrc`

**4. 启动项目**

```bash
mvn spring-boot:run
```

或在 IDEA 里点 ChatApplication 的运行按钮。

**5. 访问聊天室**

```
http://localhost:8080/chat.html
```

打开多个浏览器标签页,输入不同的用户名,即可体验多人聊天。

## 已知限制 & 后续规划

- [ ] **认证机制**:当前用户名仅作显示用途,无身份验证,存在身份伪造风险。计划引入 JWT + BCrypt 实现用户系统
- [ ] 历史消息分页加载(目前固定加载最近 50 条)
- [ ] 多房间支持
- [ ] 消息删除 / 撤回功能
- [ ] 私聊功能
- [ ] 替换 System.out 为 SLF4J 日志框架
- [ ] 容器化部署(Docker)+ 云端部署(Render / Railway)

## 关键技术决策与踩坑

### 为什么选 MySQL 8.0 而不是 9.x?
- 8.0 是当前企业绝对主流(中国 Java 后端招聘几乎都点名 8.0)
- 9.x 太新,网上资料少、Spring 生态最佳实践基于 8.x 调优

### 为什么选 MyBatis 而不是 Spring Data JPA?
- 国内 Java 后端市场绝对主流是 MyBatis
- 自己写 SQL,对数据库底层理解更深(面试讲 SQL 更有底气)
- 注解风格(@Insert / @Select)简洁,适合中小项目

### 为什么 Spring Boot 4.0.6 降级到 3.5.6?
- `mybatis-spring-boot-starter 3.0.3` 还未适配 Spring Boot 4.x 的新自动配置机制
- 报错:`Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`
- 教训:**做项目选已验证稳态版本,不要追最新**

### JDBC URL 的 `characterEncoding` 命名空间问题
- 错写为 `utf8mb4`(MySQL 字符集名),应为 `UTF-8`(Java 字符集名)
- 两者表达同一种编码,但分属不同命名空间:**JDBC 参数用 Java 字符集名,SQL CREATE 用 MySQL 字符集名**

### LocalDateTime 序列化坑
- Jackson 默认把 LocalDateTime 输出为数字数组 `[2026,6,4,14,5,29]`
- 解决：在 Spring 管理的 `ObjectMapper` 中注册 `JavaTimeModule`，并关闭 `WRITE_DATES_AS_TIMESTAMPS`
- 关键：ChatHandler 必须使用 Spring 注入的 `ObjectMapper`
- 最终输出：`"2026-06-04T14:05:29"`