package com.expirelog.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserReminderSettingDTO {

    private Long userId;
    private boolean enabled;
    private List<String> channels;
    private Integer daysBeforeExpire;
}
