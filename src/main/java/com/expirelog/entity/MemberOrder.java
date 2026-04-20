package com.expirelog.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberOrder {

    private Long id;
    private Long userId;
    private Integer durationDays;
    private String status;
    private LocalDateTime createdAt;
}
