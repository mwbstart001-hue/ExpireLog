package com.expirelog.enums;

public enum ReminderStatus {
    PENDING("PENDING", "待发送"),
    SENT("SENT", "已发送"),
    FAILED("FAILED", "发送失败"),
    SKIPPED("SKIPPED", "已跳过");

    private final String code;
    private final String desc;

    ReminderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
