package com.expirelog.entity;

import java.time.LocalDateTime;

public class MemberExpireLog {

    private Long id;
    private Long userId;
    private Integer changeDays;
    private Long orderId;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getChangeDays() {
        return changeDays;
    }

    public void setChangeDays(Integer changeDays) {
        this.changeDays = changeDays;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
