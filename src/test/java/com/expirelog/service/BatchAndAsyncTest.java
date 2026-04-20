package com.expirelog.service;

import com.expirelog.dto.BatchApplyResult;
import com.expirelog.dto.FailedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BatchAndAsyncTest extends BaseIntegrationTest {

    private long userId;

    @BeforeEach
    void setup() {
        userId = nextUserId();
    }

    @Test
    void testBatchApply_MultipleValidOrders_ShouldAllSucceed() {
        long orderId1 = nextOrderId();
        long orderId2 = nextOrderId();
        long orderId3 = nextOrderId();
        int days1 = 30;
        int days2 = 60;
        int days3 = 90;

        transactionTemplate.execute(status -> {
            orderMapper.insert(orderId1, userId, days1, "PAID", LocalDateTime.now());
            orderMapper.insert(orderId2, userId, days2, "PAID", LocalDateTime.now());
            orderMapper.insert(orderId3, userId, days3, "PAID", LocalDateTime.now());
            return null;
        });

        List<Long> orderIds = Arrays.asList(orderId1, orderId2, orderId3);
        BatchApplyResult result = memberExpireService.applyMemberExpireBatch(orderIds);

        assertEquals(3, result.getSuccess().size());
        assertTrue(result.getSuccess().containsAll(orderIds));
        assertEquals(0, result.getFailed().size());

        int logCount = expireLogMapper.countByUserId(userId);
        assertEquals(3, logCount, "应该有3条流水记录");
    }

    @Test
    void testBatchApply_PartialSuccess_ShouldReturnCorrectResults() {
        long validOrderId = nextOrderId();
        long nonExistentOrderId = nextOrderId();
        long unpaidOrderId = nextOrderId();
        int days = 30;

        transactionTemplate.execute(status -> {
            orderMapper.insert(validOrderId, userId, days, "PAID", LocalDateTime.now());
            orderMapper.insert(unpaidOrderId, userId, days, "PENDING", LocalDateTime.now());
            return null;
        });

        List<Long> orderIds = Arrays.asList(validOrderId, nonExistentOrderId, unpaidOrderId);
        BatchApplyResult result = memberExpireService.applyMemberExpireBatch(orderIds);

        assertEquals(1, result.getSuccess().size());
        assertTrue(result.getSuccess().contains(validOrderId));

        assertEquals(2, result.getFailed().size());
        FailedOrder failed1 = result.getFailed().stream()
                .filter(f -> f.getOrderId().equals(nonExistentOrderId))
                .findFirst().orElse(null);
        FailedOrder failed2 = result.getFailed().stream()
                .filter(f -> f.getOrderId().equals(unpaidOrderId))
                .findFirst().orElse(null);

        assertNotNull(failed1);
        assertNotNull(failed2);
        assertTrue(failed1.getReason().contains("订单不存在或未支付"));
        assertTrue(failed2.getReason().contains("订单不存在或未支付"));
    }

    @Test
    void testBatchApply_AlreadyProcessedOrders_ShouldStillBeInSuccess() {
        long orderId = nextOrderId();
        int days = 30;

        transactionTemplate.execute(status -> {
            orderMapper.insert(orderId, userId, days, "PAID", LocalDateTime.now());
            return null;
        });

        memberExpireService.applyMemberExpire(orderId);

        List<Long> orderIds = Arrays.asList(orderId);
        BatchApplyResult result = memberExpireService.applyMemberExpireBatch(orderIds);

        assertEquals(1, result.getSuccess().size());
        assertEquals(0, result.getFailed().size());

        int logCount = expireLogMapper.countByUserId(userId);
        assertEquals(1, logCount, "已处理的订单不会重复添加流水");
    }

    @Test
    void testBatchApply_ExceedsMaxSize_ShouldThrowException() {
        List<Long> tooManyOrders = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooManyOrders.add(nextOrderId());
        }

        assertThrows(IllegalArgumentException.class,
                () -> memberExpireService.applyMemberExpireBatch(tooManyOrders));
    }

    @Test
    void testAsyncApply_ShouldProcessInBackground() throws InterruptedException {
        long orderId = nextOrderId();
        int days = 30;

        transactionTemplate.execute(status -> {
            orderMapper.insert(orderId, userId, days, "PAID", LocalDateTime.now());
            return null;
        });

        CountDownLatch latch = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            try {
                for (int i = 0; i < 30; i++) {
                    int count = transactionTemplate.execute(status ->
                            expireLogMapper.countByUserId(userId));
                    if (count > 0) {
                        latch.countDown();
                        return;
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        waiter.start();

        long startTime = System.currentTimeMillis();
        memberExpireService.applyMemberExpireAsync(orderId);
        long asyncCallTime = System.currentTimeMillis() - startTime;

        assertTrue(asyncCallTime < 100, "异步调用应该立即返回，耗时: " + asyncCallTime + "ms");

        boolean processed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(processed, "异步任务应该在后台完成处理");

        int logCount = transactionTemplate.execute(status ->
                expireLogMapper.countByUserId(userId));
        assertEquals(1, logCount, "异步处理应该添加流水记录");
    }

    @Test
    void testPerformance_BatchVsSequential() {
        int orderCount = 50;
        List<Long> orderIds = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();

        transactionTemplate.execute(status -> {
            for (int i = 0; i < orderCount; i++) {
                long orderUserId = nextUserId();
                long orderId = nextOrderId();
                userIds.add(orderUserId);
                orderIds.add(orderId);
                orderMapper.insert(orderId, orderUserId, 30, "PAID", LocalDateTime.now());
            }
            return null;
        });

        cleanupTestData(userIds.stream().mapToLong(l -> l).toArray());

        long sequentialStart = System.nanoTime();
        for (Long orderId : orderIds) {
            memberExpireService.applyMemberExpire(orderId);
        }
        long sequentialTime = System.nanoTime() - sequentialStart;

        cleanupTestData(userIds.stream().mapToLong(l -> l).toArray());
        cleanupOrderData(orderIds.stream().mapToLong(l -> l).toArray());

        long batchStart = System.nanoTime();
        BatchApplyResult result = memberExpireService.applyMemberExpireBatch(orderIds);
        long batchTime = System.nanoTime() - batchStart;

        double sequentialMs = sequentialTime / 1_000_000.0;
        double batchMs = batchTime / 1_000_000.0;
        double improvement = (sequentialMs - batchMs) / sequentialMs * 100;

        System.out.printf("性能对比 - 处理 %d 个订单:%n", orderCount);
        System.out.printf("  顺序调用: %.2f ms%n", sequentialMs);
        System.out.printf("  批量处理: %.2f ms%n", batchMs);
        System.out.printf("  性能提升: %.1f%%%n", improvement);

        assertEquals(orderCount, result.getSuccess().size());
        assertEquals(0, result.getFailed().size());

        assertTrue(improvement > 0, "批量处理应该比顺序调用更快");
        assertTrue(batchMs < sequentialMs * 0.8,
                String.format("批量处理应该比顺序调用快至少20%%。顺序: %.2fms, 批量: %.2fms",
                        sequentialMs, batchMs));
    }
}
