package com.expirelog.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchApplyResult {
    private List<Long> success = new ArrayList<>();
    private List<FailedOrder> failed = new ArrayList<>();

    public void addSuccess(Long orderId) {
        success.add(orderId);
    }

    public void addFailed(Long orderId, String reason) {
        failed.add(new FailedOrder(orderId, reason));
    }
}
