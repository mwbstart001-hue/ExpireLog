package com.expirelog.enums;

public enum ReminderChannel {
    SMS("SMS", "短信"),
    EMAIL("EMAIL", "邮件");

    private final String code;
    private final String desc;

    ReminderChannel(String code, String desc) {
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
