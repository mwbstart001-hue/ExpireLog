package com.expirelog.service;

import com.expirelog.entity.UserMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class MemberExpireServiceTest extends BaseIntegrationTest {

    private long userId;
    private long orderId;

    @BeforeEach
    void setupTestData() {
        userId = nextUserId();
        orderId = nextOrderId();
    }

    @Test
    void testSameOrderMultipleCalls_ShouldAddOnlyOnce() {
        int durationDays = 30;
        LocalDateTime initialExpire = LocalDateTime.now().plusDays(10);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, initialExpire);
            orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
            return null;
        });

        memberExpireService.applyMemberExpire(orderId);
        LocalDateTime afterFirstCall = getExpireTime(userId);

        memberExpireService.applyMemberExpire(orderId);
        LocalDateTime afterSecondCall = getExpireTime(userId);

        memberExpireService.applyMemberExpire(orderId);
        LocalDateTime afterThirdCall = getExpireTime(userId);

        assertEquals(afterFirstCall, afterSecondCall);
        assertEquals(afterSecondCall, afterThirdCall);

        LocalDateTime expectedExpire = initialExpire.plusDays(durationDays);
        assertTrue(afterThirdCall.isEqual(expectedExpire) || 
                   afterThirdCall.isAfter(expectedExpire.minusSeconds(1)));
    }

    @Test
    void testMultipleOrders_ShouldAccumulateCorrectly() {
        long orderId1 = nextOrderId();
        long orderId2 = nextOrderId();
        long orderId3 = nextOrderId();
        int days1 = 30;
        int days2 = 60;
        int days3 = 90;

        LocalDateTime initialExpire = LocalDateTime.now().plusDays(5);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, initialExpire);
            orderMapper.insert(orderId1, userId, days1, "PAID", LocalDateTime.now());
            orderMapper.insert(orderId2, userId, days2, "PAID", LocalDateTime.now());
            orderMapper.insert(orderId3, userId, days3, "PAID", LocalDateTime.now());
            return null;
        });

        memberExpireService.applyMemberExpire(orderId1);
        LocalDateTime afterOrder1 = getExpireTime(userId);

        memberExpireService.applyMemberExpire(orderId2);
        LocalDateTime afterOrder2 = getExpireTime(userId);

        memberExpireService.applyMemberExpire(orderId3);
        LocalDateTime afterOrder3 = getExpireTime(userId);

        LocalDateTime expectedAfter1 = initialExpire.plusDays(days1);
        assertTrue(afterOrder1.isEqual(expectedAfter1) || afterOrder1.isAfter(expectedAfter1.minusSeconds(1)));

        LocalDateTime expectedAfter2 = expectedAfter1.plusDays(days2);
        assertTrue(afterOrder2.isEqual(expectedAfter2) || afterOrder2.isAfter(expectedAfter2.minusSeconds(1)));

        LocalDateTime expectedAfter3 = expectedAfter2.plusDays(days3);
        assertTrue(afterOrder3.isEqual(expectedAfter3) || afterOrder3.isAfter(expectedAfter3.minusSeconds(1)));
    }

    @Test
    void testExpiredThenPurchase_ShouldStartFromNow() {
        int durationDays = 30;
        LocalDateTime expiredTime = LocalDateTime.now().minusDays(10);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, expiredTime);
            orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
            return null;
        });

        LocalDateTime beforeCall = LocalDateTime.now();
        memberExpireService.applyMemberExpire(orderId);
        LocalDateTime afterCall = LocalDateTime.now();

        LocalDateTime expireTime = getExpireTime(userId);

        LocalDateTime lowerBound = beforeCall.plusDays(durationDays).minusMinutes(1);
        LocalDateTime upperBound = afterCall.plusDays(durationDays).plusMinutes(1);

        assertTrue(expireTime.isAfter(lowerBound) && expireTime.isBefore(upperBound),
                "到期时间应该从当前时间开始计算，而不是从已过期的时间开始");
    }

    @Test
    void testFirstTimePurchase_ShouldCreateMemberRecord() {
        int durationDays = 30;

        transactionTemplate.execute(status -> {
            orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
            return null;
        });

        UserMember beforePurchase = transactionTemplate.execute(status ->
                memberMapper.selectByUserId(userId)
        );
        assertNull(beforePurchase, "首次购买前用户没有会员记录");

        LocalDateTime beforeCall = LocalDateTime.now();
        memberExpireService.applyMemberExpire(orderId);
        LocalDateTime afterCall = LocalDateTime.now();

        UserMember afterPurchase = transactionTemplate.execute(status ->
                memberMapper.selectByUserId(userId)
        );
        assertNotNull(afterPurchase, "首次购买后用户应有会员记录");
        assertEquals(userId, afterPurchase.getUserId());

        LocalDateTime expireTime = afterPurchase.getExpireTime();
        LocalDateTime lowerBound = beforeCall.plusDays(durationDays).minusMinutes(1);
        LocalDateTime upperBound = afterCall.plusDays(durationDays).plusMinutes(1);

        assertTrue(expireTime.isAfter(lowerBound) && expireTime.isBefore(upperBound),
                "首次购买的到期时间应该从当前时间开始计算");
    }

    @Test
    void testConcurrentCallbacks_ShouldNotCorruptExpireTime() throws InterruptedException {
        int threadCount = 10;
        int durationDays = 30;
        LocalDateTime initialExpire = LocalDateTime.now().plusDays(10);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, initialExpire);
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

        LocalDateTime finalExpireTime = getExpireTime(userId);
        LocalDateTime expectedExpireTime = initialExpire.plusDays(durationDays);

        assertTrue(finalExpireTime.isEqual(expectedExpireTime) || 
                   finalExpireTime.isAfter(expectedExpireTime.minusSeconds(1)),
                "并发情况下，到期时间应该只增加一次，最终值应该是初始值 + 30天");

        assertEquals(threadCount, successCount.get(), 
                "所有并发调用都应该成功返回，不应该抛出异常");
    }

    private LocalDateTime getExpireTime(long userId) {
        return transactionTemplate.execute(status -> {
            UserMember member = memberMapper.selectByUserId(userId);
            return member != null ? member.getExpireTime() : null;
        });
    }
}
