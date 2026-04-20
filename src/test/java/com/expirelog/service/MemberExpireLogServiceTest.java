package com.expirelog.service;

import com.expirelog.dto.MemberExpireLogDTO;
import com.expirelog.dto.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("会员流水查询集成测试")
class MemberExpireLogServiceTest extends BaseIntegrationTest {

    @Autowired
    private MemberExpireLogService memberExpireLogService;

    private long userId;
    private long orderId1;
    private long orderId2;
    private long orderId3;

    @BeforeEach
    void setupTestData() {
        userId = nextUserId();
        orderId1 = nextOrderId();
        orderId2 = nextOrderId();
        orderId3 = nextOrderId();

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, LocalDateTime.now().plusDays(10));
            orderMapper.insert(orderId1, userId, 30, "PAID", LocalDateTime.now().minusHours(2));
            orderMapper.insert(orderId2, userId, 60, "PAID", LocalDateTime.now().minusHours(1));
            orderMapper.insert(orderId3, userId, 90, "PAID", LocalDateTime.now());
            return null;
        });

        memberExpireService.applyMemberExpire(orderId1);
        memberExpireService.applyMemberExpire(orderId2);
        memberExpireService.applyMemberExpire(orderId3);
    }

    @Test
    @DisplayName("按订单 ID 查询流水：存在的订单")
    void testGetByOrderId_ExistingOrder() {
        MemberExpireLogDTO log = memberExpireLogService.getByOrderId(orderId1);

        assertNotNull(log);
        assertEquals(userId, log.getUserId());
        assertEquals(30, log.getChangeDays());
        assertEquals(orderId1, log.getOrderId());
        assertNotNull(log.getCreatedAt());
    }

    @Test
    @DisplayName("按订单 ID 查询流水：不存在的订单")
    void testGetByOrderId_NonExistingOrder() {
        long nonExistingOrderId = nextOrderId() + 10000;

        MemberExpireLogDTO log = memberExpireLogService.getByOrderId(nonExistingOrderId);

        assertNull(log);
    }

    @Test
    @DisplayName("按用户 ID 查询流水：第一页")
    void testGetByUserId_FirstPage() {
        PageResult<MemberExpireLogDTO> result = memberExpireLogService.getByUserId(userId, 1, 10);

        assertNotNull(result);
        assertEquals(3, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(1, result.getTotalPages());
        assertEquals(3, result.getData().size());

        List<MemberExpireLogDTO> logs = result.getData();
        assertEquals(orderId3, logs.get(0).getOrderId());
        assertEquals(orderId2, logs.get(1).getOrderId());
        assertEquals(orderId1, logs.get(2).getOrderId());
    }

    @Test
    @DisplayName("按用户 ID 查询流水：分页逻辑")
    void testGetByUserId_Pagination() {
        PageResult<MemberExpireLogDTO> page1 = memberExpireLogService.getByUserId(userId, 1, 2);

        assertEquals(3, page1.getTotal());
        assertEquals(1, page1.getPage());
        assertEquals(2, page1.getSize());
        assertEquals(2, page1.getTotalPages());
        assertEquals(2, page1.getData().size());

        PageResult<MemberExpireLogDTO> page2 = memberExpireLogService.getByUserId(userId, 2, 2);

        assertEquals(3, page2.getTotal());
        assertEquals(2, page2.getPage());
        assertEquals(2, page2.getSize());
        assertEquals(2, page2.getTotalPages());
        assertEquals(1, page2.getData().size());

        assertEquals(page1.getData().get(0).getOrderId(), orderId3);
        assertEquals(page1.getData().get(1).getOrderId(), orderId2);
        assertEquals(page2.getData().get(0).getOrderId(), orderId1);
    }

    @Test
    @DisplayName("按用户 ID 查询流水：页面大小限制 100")
    void testGetByUserId_PageSizeLimit() {
        PageResult<MemberExpireLogDTO> result = memberExpireLogService.getByUserId(userId, 1, 200);

        assertNotNull(result);
        assertEquals(100, result.getSize());
    }

    @Test
    @DisplayName("按用户 ID 查询流水：无效页面大小使用默认值")
    void testGetByUserId_InvalidPageSize() {
        PageResult<MemberExpireLogDTO> result = memberExpireLogService.getByUserId(userId, 1, -5);

        assertNotNull(result);
        assertEquals(20, result.getSize());
    }

    @Test
    @DisplayName("按用户 ID 查询流水：无效页码从第一页开始")
    void testGetByUserId_InvalidPage() {
        PageResult<MemberExpireLogDTO> result = memberExpireLogService.getByUserId(userId, -1, 10);

        assertNotNull(result);
        assertEquals(1, result.getPage());
    }

    @Test
    @DisplayName("按用户 ID 查询流水：无数据返回空列表")
    void testGetByUserId_NoData() {
        long nonExistingUserId = nextUserId() + 10000;

        PageResult<MemberExpireLogDTO> result = memberExpireLogService.getByUserId(nonExistingUserId, 1, 10);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getData().size());
    }

    @Test
    @DisplayName("流水记录排序：按创建时间倒序")
    void testGetByUserId_OrderByCreatedAtDesc() {
        PageResult<MemberExpireLogDTO> result = memberExpireLogService.getByUserId(userId, 1, 10);

        List<MemberExpireLogDTO> logs = result.getData();
        assertEquals(3, logs.size());

        for (int i = 1; i < logs.size(); i++) {
            MemberExpireLogDTO current = logs.get(i - 1);
            MemberExpireLogDTO next = logs.get(i);
            assertTrue(current.getCreatedAt().isAfter(next.getCreatedAt()) ||
                    current.getCreatedAt().isEqual(next.getCreatedAt()));
        }
    }
}
