package com.expirelog.dto;

import lombok.Data;

@Data
public class FailedOrder {
    private Long orderId;
    private String reason;

    public FailedOrder(Long orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }
}
