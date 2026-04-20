package com.expirelog.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchApplyRequest {
    private List<Long> orderIds;
}
