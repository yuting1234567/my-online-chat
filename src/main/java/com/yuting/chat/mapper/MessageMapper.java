package com.yuting.chat.mapper;

import com.yuting.chat.entity.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MessageMapper {
    /**
    * 插入一条消息。id 和 create_at 由数据库自动生成
    */
    @Insert("INSERT INTO messages(username, content) VALUES (#{username}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertMessage(Message message);

    /**
    * 查询最近的 N 条消息。返回的列表按 id 倒序（最新的在前）
    */
    @Select("SELECT id, username, content, created_at FROM messages ORDER BY id DESC LIMIT #{limit}")
    List<Message> findRecent(int limit);
}
