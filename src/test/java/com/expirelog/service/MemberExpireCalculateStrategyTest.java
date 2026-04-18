package com.expirelog.service;

import com.expirelog.config.MemberExpireProperties;
import com.expirelog.entity.UserMember;
import com.expirelog.enums.MemberAccumulationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("会员到期时间计算策略测试")
class MemberExpireCalculateStrategyTest {

    private MemberExpireService service;
    private MemberExpireProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MemberExpireProperties();
        service = new MemberExpireService(null, null, null, properties);
    }

    @Test
    @DisplayName("首次购买：member 为 null 时，从当前时间开始计算")
    void testFirstTimePurchase() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 10, 0);
        int durationDays = 30;

        LocalDateTime result = service.calculateNewExpireTime(null, durationDays, now);

        LocalDateTime expected = now.plusDays(durationDays);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("STRICT 策略：永远从原到期时间累加，不管是否过期")
    void testStrictStrategy() {
        properties.setStrategy(MemberAccumulationStrategy.STRICT);

        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 8, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 10, 0);
        int durationDays = 30;

        UserMember member = createUserMember(oldExpire);
        LocalDateTime result = service.calculateNewExpireTime(member, durationDays, now);

        LocalDateTime expected = oldExpire.plusDays(durationDays);
        assertEquals(expected, result);
        assertEquals(LocalDateTime.of(2026, 5, 8, 0, 0), result);
    }

    @Test
    @DisplayName("RESET 策略：未过期从原到期时间累加")
    void testResetStrategy_NotExpired() {
        properties.setStrategy(MemberAccumulationStrategy.RESET);

        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 28, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 10, 0);
        int durationDays = 30;

        UserMember member = createUserMember(oldExpire);
        LocalDateTime result = service.calculateNewExpireTime(member, durationDays, now);

        LocalDateTime expected = oldExpire.plusDays(durationDays);
        assertEquals(expected, result);
        assertEquals(LocalDateTime.of(2026, 5, 28, 0, 0), result);
    }

    @Test
    @DisplayName("RESET 策略：已过期从当前时间开始")
    void testResetStrategy_Expired() {
        properties.setStrategy(MemberAccumulationStrategy.RESET);

        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 8, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 4, 18, 10, 0);
        int durationDays = 30;

        UserMember member = createUserMember(oldExpire);
        LocalDateTime result = service.calculateNewExpireTime(member, durationDays, now);

        LocalDateTime expected = now.plusDays(durationDays);
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @CsvSource({
            "7, 2026-04-12, 2026-05-08",
            "7, 2026-04-15, 2026-05-08",
            "7, 2026-04-16, 2026-05-18"
    })
    @DisplayName("GRACE 策略：宽限期内从原到期时间，超出宽限期从当前时间")
    void testGraceStrategy(int graceDays, String nowStr, String expectedStr) {
        properties.setStrategy(MemberAccumulationStrategy.GRACE);
        properties.setGraceDays(graceDays);

        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 8, 0, 0);
        LocalDateTime now = LocalDateTime.parse(nowStr + "T10:00:00");
        LocalDateTime expected = LocalDateTime.parse(expectedStr + "T00:00:00");
        int durationDays = 30;

        UserMember member = createUserMember(oldExpire);
        LocalDateTime result = service.calculateNewExpireTime(member, durationDays, now);

        assertEquals(expected, result.toLocalDate().atStartOfDay());
    }

    @Test
    @DisplayName("GRACE 策略：宽限期边界测试（刚好在宽限期最后一天）")
    void testGraceStrategy_Boundary() {
        properties.setStrategy(MemberAccumulationStrategy.GRACE);
        properties.setGraceDays(7);

        LocalDateTime oldExpire = LocalDateTime.of(2026, 4, 8, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 4, 15, 0, 0);
        int durationDays = 30;

        UserMember member = createUserMember(oldExpire);
        LocalDateTime result = service.calculateNewExpireTime(member, durationDays, now);

        LocalDateTime expected = oldExpire.plusDays(durationDays);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("枚举转换：null 值返回默认 RESET")
    void testEnumFromNull() {
        MemberAccumulationStrategy result = MemberAccumulationStrategy.fromString(null);
        assertEquals(MemberAccumulationStrategy.RESET, result);
    }

    @Test
    @DisplayName("枚举转换：无效值返回默认 RESET")
    void testEnumFromInvalid() {
        MemberAccumulationStrategy result = MemberAccumulationStrategy.fromString("INVALID");
        assertEquals(MemberAccumulationStrategy.RESET, result);
    }

    @Test
    @DisplayName("枚举转换：有效字符串不区分大小写")
    void testEnumFromValid() {
        assertEquals(MemberAccumulationStrategy.STRICT, MemberAccumulationStrategy.fromString("strict"));
        assertEquals(MemberAccumulationStrategy.GRACE, MemberAccumulationStrategy.fromString("GRACE"));
        assertEquals(MemberAccumulationStrategy.RESET, MemberAccumulationStrategy.fromString("Reset"));
    }

    @Test
    @DisplayName("配置类：graceDays 不能为负数")
    void testGraceDays_NonNegative() {
        properties.setGraceDays(-1);
        assertEquals(0, properties.getGraceDays());

        properties.setGraceDays(7);
        assertEquals(7, properties.getGraceDays());
    }

    private UserMember createUserMember(LocalDateTime expireTime) {
        UserMember member = new UserMember();
        member.setUserId(1L);
        member.setExpireTime(expireTime);
        return member;
    }
}
