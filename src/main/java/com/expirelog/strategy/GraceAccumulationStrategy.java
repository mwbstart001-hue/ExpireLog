package com.expirelog.strategy;

import com.expirelog.enums.MemberAccumulationStrategy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class GraceAccumulationStrategy implements AccumulationStrategy {

    @Override
    public LocalDateTime calculateBaseTime(LocalDateTime oldExpire, LocalDateTime now, int graceDays) {
        LocalDateTime graceEndTime = oldExpire.plusDays(graceDays);
        if (now.isBefore(graceEndTime) || now.isEqual(graceEndTime)) {
            return oldExpire;
        } else {
            return now;
        }
    }

    public MemberAccumulationStrategy getStrategyType() {
        return MemberAccumulationStrategy.GRACE;
    }
}
