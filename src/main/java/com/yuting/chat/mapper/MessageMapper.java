package com.yuting.chat.mapper;

import com.yuting.chat.entity.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MessageMapper {
    /**
    * 插入一条消息。id 和 create_at 由数据库自动生成
    */
    @Insert("INSERT INTO messages(username, to_username, content) VALUES (#{username}, #{toUsername}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertMessage(Message message);

    /**
    * 查询最近的 N 条消息。返回的列表按 id 倒序（最新的在前）
    */
    @Select("SELECT id, username, content, created_at FROM messages WHERE to_username is null ORDER BY id DESC LIMIT #{limit}")
    List<Message> findRecentGroup(@Param("limit") int limit);

    /**
     * 查询发给某用户的未送达私聊消息,按时间升序(最老的先推)
     */
    @Select("SELECT id, username, to_username, content, delivered, created_at FROM messages WHERE to_username = #{toUsername} and delivered = 0 ORDER BY id ASC")
    List<Message> findUndeliveredPrivate(@Param("toUsername") String toUsername);

    /**
     * 标记消息为已送达
     */
    @Update("UPDATE messages SET delivered = 1 WHERE id = #{id}")
    void markDelivered(@Param("id") Long id);
}
