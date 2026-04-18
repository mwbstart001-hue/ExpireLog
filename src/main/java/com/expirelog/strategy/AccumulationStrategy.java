package com.expirelog.strategy;

import com.expirelog.enums.MemberAccumulationStrategy;

import java.time.LocalDateTime;

public interface AccumulationStrategy {

    LocalDateTime calculateBaseTime(LocalDateTime oldExpire, LocalDateTime now, int graceDays);

    MemberAccumulationStrategy getStrategyType();
}
