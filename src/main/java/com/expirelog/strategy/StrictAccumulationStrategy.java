package com.expirelog.strategy;

import com.expirelog.enums.MemberAccumulationStrategy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class StrictAccumulationStrategy implements AccumulationStrategy {

    @Override
    public LocalDateTime calculateBaseTime(LocalDateTime oldExpire, LocalDateTime now, int graceDays) {
        return oldExpire;
    }

    public MemberAccumulationStrategy getStrategyType() {
        return MemberAccumulationStrategy.STRICT;
    }
}
