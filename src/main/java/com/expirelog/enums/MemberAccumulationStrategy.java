package com.expirelog.enums;

public enum MemberAccumulationStrategy {

    STRICT,

    GRACE,

    RESET;

    public static MemberAccumulationStrategy fromString(String value) {
        if (value == null) {
            return RESET;
        }
        for (MemberAccumulationStrategy strategy : values()) {
            if (strategy.name().equalsIgnoreCase(value)) {
                return strategy;
            }
        }
        return RESET;
    }
}
