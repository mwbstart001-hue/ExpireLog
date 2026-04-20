package com.expirelog.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserMemberDTO {

    private Long userId;
    private LocalDateTime expireTime;
}
