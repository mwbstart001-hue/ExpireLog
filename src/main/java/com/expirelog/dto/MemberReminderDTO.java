package com.expirelog.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberReminderDTO {

    private Long id;
    private Long userId;
    private LocalDateTime remindTime;
    private String channel;
    private String status;
    private LocalDateTime createdAt;
}
