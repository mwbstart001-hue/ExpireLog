package com.expirelog.service;

import com.expirelog.entity.UserMember;
import com.expirelog.mapper.MemberExpireLogMapper;
import com.expirelog.mapper.MemberOrderMapper;
import com.expirelog.mapper.UserMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MemberExpireServiceTest {

    @Autowired
    private MemberExpireService memberExpireService;

    @Autowired
    private MemberOrderMapper orderMapper;

    @Autowired
    private UserMemberMapper memberMapper;

    @Autowired
    private MemberExpireLogMapper expireLogMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void testSameOrderMultipleCalls_ShouldAddOnlyOnce() {
        Long userId = 1L;
        Long orderId = 100L;
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
        Long userId = 2L;
        Long orderId1 = 201L;
        Long orderId2 = 202L;
        Long orderId3 = 203L;
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
        Long userId = 3L;
        Long orderId = 300L;
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
    void testConcurrentCallbacks_ShouldNotCorruptExpireTime() throws InterruptedException {
        int threadCount = 10;
        Long userId = 4L;
        Long orderId = 400L;
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

    private LocalDateTime getExpireTime(Long userId) {
        return transactionTemplate.execute(status -> {
            UserMember member = memberMapper.selectByUserId(userId);
            return member != null ? member.getExpireTime() : null;
        });
    }
}
