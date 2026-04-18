package com.expirelog.service;

import com.expirelog.config.MemberExpireProperties;
import com.expirelog.enums.MemberAccumulationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("三种策略集成测试")
class MemberExpireStrategyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MemberExpireProperties properties;

    private long userId;
    private long orderId;

    @BeforeEach
    void setup() {
        userId = nextUserId();
        orderId = nextOrderId();
    }

    @Nested
    @DisplayName("RESET 策略测试（默认）")
    class ResetStrategyTests {

        @BeforeEach
        void setStrategy() {
            properties.setStrategy(MemberAccumulationStrategy.RESET);
        }

        @Test
        @DisplayName("未过期：从原到期时间累加")
        void testNotExpired_ShouldUseOldExpire() {
            int durationDays = 30;
            LocalDateTime oldExpire = LocalDateTime.now().plusDays(10);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, oldExpire);
                orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
                return null;
            });

            memberExpireService.applyMemberExpire(orderId);

            LocalDateTime newExpire = getExpireTime(userId);
            LocalDateTime expected = oldExpire.plusDays(durationDays);

            assertTrue(newExpire.isEqual(expected) || newExpire.isAfter(expected.minusSeconds(1)),
                    "未过期应该从原到期时间累加");
        }

        @Test
        @DisplayName("已过期：从当前时间开始")
        void testExpired_ShouldUseNow() {
            int durationDays = 30;
            LocalDateTime oldExpire = LocalDateTime.now().minusDays(10);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, oldExpire);
                orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
                return null;
            });

            LocalDateTime beforeCall = LocalDateTime.now();
            memberExpireService.applyMemberExpire(orderId);
            LocalDateTime afterCall = LocalDateTime.now();

            LocalDateTime newExpire = getExpireTime(userId);
            LocalDateTime lowerBound = beforeCall.plusDays(durationDays).minusMinutes(1);
            LocalDateTime upperBound = afterCall.plusDays(durationDays).plusMinutes(1);

            assertTrue(newExpire.isAfter(lowerBound) && newExpire.isBefore(upperBound),
                    "已过期应该从当前时间开始");
        }
    }

    @Nested
    @DisplayName("STRICT 策略测试")
    class StrictStrategyTests {

        @BeforeEach
        void setStrategy() {
            properties.setStrategy(MemberAccumulationStrategy.STRICT);
        }

        @Test
        @DisplayName("永远从原到期时间累加，不管是否过期")
        void testAlwaysUseOldExpire() {
            int durationDays = 30;
            LocalDateTime oldExpire = LocalDateTime.now().minusDays(10);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, oldExpire);
                orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
                return null;
            });

            memberExpireService.applyMemberExpire(orderId);

            LocalDateTime newExpire = getExpireTime(userId);
            LocalDateTime expected = oldExpire.plusDays(durationDays);

            assertEquals(expected, newExpire, "STRICT 策略不管是否过期都从原到期时间累加");
        }

        @Test
        @DisplayName("并发测试：10 线程同一订单，流水只写一条")
        void testConcurrentSameOrder_ShouldWriteOnce() throws InterruptedException {
            int threadCount = 10;
            int durationDays = 30;
            LocalDateTime oldExpire = LocalDateTime.now().minusDays(10);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, oldExpire);
                orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
                return null;
            });

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        memberExpireService.applyMemberExpire(orderId);
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await();
            executorService.shutdown();

            LocalDateTime newExpire = getExpireTime(userId);
            LocalDateTime expected = oldExpire.plusDays(durationDays);

            assertEquals(expected, newExpire, "到期时间只增加一次");
            assertEquals(threadCount, successCount.get(), "所有线程都应该成功返回");
        }
    }

    @Nested
    @DisplayName("GRACE 策略测试")
    class GraceStrategyTests {

        private static final int GRACE_DAYS = 7;

        @BeforeEach
        void setStrategy() {
            properties.setStrategy(MemberAccumulationStrategy.GRACE);
            properties.setGraceDays(GRACE_DAYS);
        }

        @Test
        @DisplayName("宽限期内：从原到期时间累加")
        void testWithinGracePeriod_ShouldUseOldExpire() {
            int durationDays = 30;
            LocalDateTime oldExpire = LocalDateTime.now().minusDays(5);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, oldExpire);
                orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
                return null;
            });

            memberExpireService.applyMemberExpire(orderId);

            LocalDateTime newExpire = getExpireTime(userId);
            LocalDateTime expected = oldExpire.plusDays(durationDays);

            assertEquals(expected, newExpire, "宽限期内从原到期时间累加");
        }

        @Test
        @DisplayName("超出宽限期：从当前时间开始")
        void testBeyondGracePeriod_ShouldUseNow() {
            int durationDays = 30;
            LocalDateTime oldExpire = LocalDateTime.now().minusDays(GRACE_DAYS + 1);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, oldExpire);
                orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
                return null;
            });

            LocalDateTime beforeCall = LocalDateTime.now();
            memberExpireService.applyMemberExpire(orderId);
            LocalDateTime afterCall = LocalDateTime.now();

            LocalDateTime newExpire = getExpireTime(userId);
            LocalDateTime lowerBound = beforeCall.plusDays(durationDays).minusMinutes(1);
            LocalDateTime upperBound = afterCall.plusDays(durationDays).plusMinutes(1);

            assertTrue(newExpire.isAfter(lowerBound) && newExpire.isBefore(upperBound),
                    "超出宽限期从当前时间开始");
        }

        @Test
        @DisplayName("宽限期边界：刚好在宽限期最后一天")
        void testGraceBoundary_AtLastDay() {
            int durationDays = 30;
            LocalDateTime oldExpire = LocalDateTime.now().minusDays(GRACE_DAYS);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, oldExpire);
                orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
                return null;
            });

            memberExpireService.applyMemberExpire(orderId);

            LocalDateTime newExpire = getExpireTime(userId);
            LocalDateTime expected = oldExpire.plusDays(durationDays);

            assertEquals(expected, newExpire, "宽限期最后一天应该算在宽限期内");
        }

        @Test
        @DisplayName("宽限期边界：宽限期后第一天")
        void testGraceBoundary_AfterLastDay() {
            int durationDays = 30;
            LocalDateTime oldExpire = LocalDateTime.now().minusDays(GRACE_DAYS).minusMinutes(1);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, oldExpire);
                orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
                return null;
            });

            LocalDateTime beforeCall = LocalDateTime.now();
            memberExpireService.applyMemberExpire(orderId);
            LocalDateTime afterCall = LocalDateTime.now();

            LocalDateTime newExpire = getExpireTime(userId);
            LocalDateTime lowerBound = beforeCall.plusDays(durationDays).minusMinutes(1);
            LocalDateTime upperBound = afterCall.plusDays(durationDays).plusMinutes(1);

            assertTrue(newExpire.isAfter(lowerBound) && newExpire.isBefore(upperBound),
                    "宽限期后第一天应该从当前时间开始");
        }
    }

    private LocalDateTime getExpireTime(long userId) {
        return transactionTemplate.execute(status -> {
            com.expirelog.entity.UserMember member = memberMapper.selectByUserId(userId);
            return member != null ? member.getExpireTime() : null;
        });
    }
}
