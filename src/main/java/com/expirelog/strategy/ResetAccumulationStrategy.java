package com.expirelog.strategy;

import com.expirelog.enums.MemberAccumulationStrategy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ResetAccumulationStrategy implements AccumulationStrategy {

    @Override
    public LocalDateTime calculateBaseTime(LocalDateTime oldExpire, LocalDateTime now, int graceDays) {
        return oldExpire.isAfter(now) ? oldExpire : now;
    }

    public MemberAccumulationStrategy getStrategyType() {
        return MemberAccumulationStrategy.RESET;
    }
}
