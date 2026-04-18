package com.expirelog.strategy;

import com.expirelog.enums.MemberAccumulationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("策略类单元测试")
class AccumulationStrategyTest {

    private final ResetAccumulationStrategy resetStrategy = new ResetAccumulationStrategy();
    private final StrictAccumulationStrategy strictStrategy = new StrictAccumulationStrategy();
    private final GraceAccumulationStrategy graceStrategy = new GraceAccumulationStrategy();

    @Test
    @DisplayName("Reset策略：未过期从原到期时间累加")
    void testReset_NotExpired() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 28, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 10, 0);

        LocalDateTime baseTime = resetStrategy.calculateBaseTime(oldExpire, now, 0);

        assertEquals(oldExpire, baseTime);
        assertEquals(MemberAccumulationStrategy.RESET, resetStrategy.getStrategyType());
    }

    @Test
    @DisplayName("Reset策略：已过期从当前时间开始")
    void testReset_Expired() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 8, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 10, 0);

        LocalDateTime baseTime = resetStrategy.calculateBaseTime(oldExpire, now, 0);

        assertEquals(now, baseTime);
    }

    @Test
    @DisplayName("Strict策略：永远从原到期时间累加，不管是否过期")
    void testStrict_AlwaysUseOldExpire() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 8, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 10, 0);

        LocalDateTime baseTime = strictStrategy.calculateBaseTime(oldExpire, now, 0);

        assertEquals(oldExpire, baseTime);
        assertEquals(MemberAccumulationStrategy.STRICT, strictStrategy.getStrategyType());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-04-12, 7, true",
            "2026-04-15, 7, true",
            "2026-04-16, 7, false"
    })
    @DisplayName("Grace策略：宽限期内从原到期时间，超出宽限期从当前时间")
    void testGrace_GracePeriod(String nowStr, int graceDays, boolean useOldExpire) {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 8, 0, 0);
        LocalDateTime now = LocalDateTime.parse(nowStr + "T10:00:00");

        LocalDateTime baseTime = graceStrategy.calculateBaseTime(oldExpire, now, graceDays);

        if (useOldExpire) {
            assertEquals(oldExpire, baseTime);
        } else {
            assertEquals(now, baseTime);
        }
        assertEquals(MemberAccumulationStrategy.GRACE, graceStrategy.getStrategyType());
    }

    @Test
    @DisplayName("Grace策略：宽限期边界测试（刚好在宽限期最后一天）")
    void testGrace_Boundary() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 8, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 4, 15, 0, 0);
        int graceDays = 7;

        LocalDateTime baseTime = graceStrategy.calculateBaseTime(oldExpire, now, graceDays);

        assertEquals(oldExpire, baseTime);
    }
}
