package com.yuting.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private Long          id;
    private String        username;
    private String        toUsername;
    private String        content;
    private Boolean       delivered;
    private LocalDateTime createdAt;
}
