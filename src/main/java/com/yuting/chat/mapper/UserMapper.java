package com.yuting.chat.mapper;

import com.yuting.chat.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper {

    /**
     * 插入一个新用户,id 由数据库自增。
     */
    @Insert("INSERT INTO users (username, password_hash) VALUES (#{username}, #{passwordHash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);

    /**
     * 按用户名查询用户。找不到返回 null。
     * 登录时用这个查出哈希,再和用户传入的密码比对。
     */
    @Select("SELECT id, username, password_hash, created_at FROM users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT username FROM users")
    List<String> findAllUsernames();
}
