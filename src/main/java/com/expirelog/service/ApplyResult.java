package com.expirelog.service;

public class ApplyResult {
    private final Status status;
    private final String reason;

    private ApplyResult(Status status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public static ApplyResult success() {
        return new ApplyResult(Status.SUCCESS, null);
    }

    public static ApplyResult alreadyProcessed() {
        return new ApplyResult(Status.ALREADY_PROCESSED, null);
    }

    public static ApplyResult failed(String reason) {
        return new ApplyResult(Status.FAILED, reason);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isAlreadyProcessed() {
        return status == Status.ALREADY_PROCESSED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public String getReason() {
        return reason;
    }

    public enum Status {
        SUCCESS,
        ALREADY_PROCESSED,
        FAILED
    }
}
