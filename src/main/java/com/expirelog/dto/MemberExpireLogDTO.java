package com.expirelog.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberExpireLogDTO {

    private Long id;
    private Long userId;
    private Integer changeDays;
    private Long orderId;
    private LocalDateTime createdAt;
}
