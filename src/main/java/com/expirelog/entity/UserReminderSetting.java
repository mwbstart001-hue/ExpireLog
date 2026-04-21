package com.expirelog.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserReminderSetting {

    private Long id;
    private Long userId;
    private boolean enabled;
    private String channels;
    private Integer daysBeforeExpire;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
