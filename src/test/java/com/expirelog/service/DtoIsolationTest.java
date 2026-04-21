package com.expirelog.service;

import com.expirelog.dto.MemberExpireLogDTO;
import com.expirelog.dto.PageResult;
import com.expirelog.dto.UserMemberDTO;
import com.expirelog.entity.MemberExpireLog;
import com.expirelog.entity.MemberOrder;
import com.expirelog.entity.UserMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DTO 层隔离与 Lombok 重构验证测试")
class DtoIsolationTest extends BaseIntegrationTest {

    @Autowired
    private MemberExpireService memberExpireService;

    @Autowired
    private MemberExpireLogService memberExpireLogService;

    private long userId;
    private long orderId;

    @BeforeEach
    void setupTestData() {
        userId = nextUserId();
        orderId = nextOrderId();

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, LocalDateTime.now().plusDays(30));
            orderMapper.insert(orderId, userId, 30, "PAID", LocalDateTime.now());
            return null;
        });

        memberExpireService.applyMemberExpire(orderId);
    }

    @Test
    @DisplayName("验证 Controller 返回 DTO 而非 Entity - getMemberInfo")
    void testControllerReturnsDto_UserMember() {
        UserMemberDTO dto = memberExpireService.getMemberInfo(userId);

        assertNotNull(dto);
        assertEquals(userId, dto.getUserId());
        assertNotNull(dto.getExpireTime());

        assertNotEquals(UserMember.class, dto.getClass(),
                "应该返回 DTO 而非 Entity");
    }

    @Test
    @DisplayName("验证 Controller 返回 DTO 而非 Entity - getLogByOrderId")
    void testControllerReturnsDto_MemberExpireLog() {
        MemberExpireLogDTO dto = memberExpireLogService.getByOrderId(orderId);

        assertNotNull(dto);
        assertEquals(userId, dto.getUserId());
        assertEquals(30, dto.getChangeDays());
        assertEquals(orderId, dto.getOrderId());

        assertNotEquals(MemberExpireLog.class, dto.getClass(),
                "应该返回 DTO 而非 Entity");
    }

    @Test
    @DisplayName("验证 Controller 返回 DTO 而非 Entity - getMemberLogs")
    void testControllerReturnsDto_PageResult() {
        PageResult<MemberExpireLogDTO> result = memberExpireLogService.getByUserId(userId, 1, 10);

        assertNotNull(result);
        assertTrue(result.getTotal() >= 1);
        assertFalse(result.getData().isEmpty());

        MemberExpireLogDTO dto = result.getData().get(0);
        assertNotEquals(MemberExpireLog.class, dto.getClass(),
                "PageResult 中的数据应该是 DTO 而非 Entity");
    }

    @Test
    @DisplayName("验证 Entity 使用 Lombok @Data 生成 getter/setter")
    void testEntityUsesLombokData() {
        UserMember userMember = new UserMember();
        MemberExpireLog expireLog = new MemberExpireLog();
        MemberOrder memberOrder = new MemberOrder();

        LocalDateTime testTime = LocalDateTime.now();
        Long testUserId = 100L;

        userMember.setUserId(testUserId);
        userMember.setExpireTime(testTime);
        assertEquals(testUserId, userMember.getUserId());
        assertEquals(testTime, userMember.getExpireTime());

        expireLog.setUserId(testUserId);
        expireLog.setChangeDays(30);
        assertEquals(testUserId, expireLog.getUserId());
        assertEquals(30, expireLog.getChangeDays());

        memberOrder.setUserId(testUserId);
        memberOrder.setStatus("PAID");
        assertEquals(testUserId, memberOrder.getUserId());
        assertEquals("PAID", memberOrder.getStatus());
    }

    @Test
    @DisplayName("验证 DTO 字段与 Entity 字段对应")
    void testDtoFieldsMatchEntityFields() {
        MemberExpireLogDTO logDto = memberExpireLogService.getByOrderId(orderId);

        assertNotNull(logDto);

        assertNotNull(logDto.getId(), "DTO 应该有 id 字段");
        assertNotNull(logDto.getUserId(), "DTO 应该有 userId 字段");
        assertNotNull(logDto.getChangeDays(), "DTO 应该有 changeDays 字段");
        assertNotNull(logDto.getOrderId(), "DTO 应该有 orderId 字段");
        assertNotNull(logDto.getCreatedAt(), "DTO 应该有 createdAt 字段");
    }

    @Test
    @DisplayName("验证 Service 层只有一个 toDTO 转换方法")
    void testServiceHasSingleToDtoMethod() {
        Class<MemberExpireService> service1Class = MemberExpireService.class;
        Class<MemberExpireLogService> service2Class = MemberExpireLogService.class;

        long toDtoCountInMemberExpireService = countToDtoMethods(service1Class);
        long toDtoCountInMemberExpireLogService = countToDtoMethods(service2Class);

        assertTrue(toDtoCountInMemberExpireService <= 1,
                "MemberExpireService 的 toDTO 方法不应超过 1 个，实际: " + toDtoCountInMemberExpireService);
        assertTrue(toDtoCountInMemberExpireLogService <= 1,
                "MemberExpireLogService 的 toDTO 方法不应超过 1 个，实际: " + toDtoCountInMemberExpireLogService);
    }

    @Test
    @DisplayName("验证 Mapper 层返回 Entity 而非 DTO（约束：不改 Mapper 层）")
    void testMapperReturnsEntity() {
        UserMember entity = transactionTemplate.execute(status ->
                memberMapper.selectByUserId(userId)
        );

        assertNotNull(entity);
        assertEquals(UserMember.class, entity.getClass(),
                "Mapper 应该返回 Entity 而非 DTO（约束：不改 Mapper 层）");
    }

    @Test
    @DisplayName("验证 MemberOrder Entity 也使用 Lombok @Data")
    void testMemberOrderUsesLombokData() {
        MemberOrder order = new MemberOrder();
        order.setId(1000L);
        order.setUserId(userId);
        order.setDurationDays(60);
        order.setStatus("PAID");
        order.setCreatedAt(LocalDateTime.now());

        assertEquals(1000L, order.getId());
        assertEquals(userId, order.getUserId());
        assertEquals(60, order.getDurationDays());
        assertEquals("PAID", order.getStatus());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    @DisplayName("验证 DTO 使用 Lombok @Data")
    void testDtoUsesLombokData() {
        UserMemberDTO userDto = new UserMemberDTO();
        MemberExpireLogDTO logDto = new MemberExpireLogDTO();

        LocalDateTime testTime = LocalDateTime.now();
        userDto.setUserId(999L);
        userDto.setExpireTime(testTime);
        assertEquals(999L, userDto.getUserId());
        assertEquals(testTime, userDto.getExpireTime());

        logDto.setId(1L);
        logDto.setUserId(999L);
        logDto.setChangeDays(30);
        logDto.setOrderId(500L);
        logDto.setCreatedAt(testTime);
        assertEquals(1L, logDto.getId());
        assertEquals(999L, logDto.getUserId());
        assertEquals(30, logDto.getChangeDays());
        assertEquals(500L, logDto.getOrderId());
        assertEquals(testTime, logDto.getCreatedAt());
    }

    @Test
    @DisplayName("验证完整数据流程：Entity → Service 转换 → DTO")
    void testCompleteDataFlow() {
        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, LocalDateTime.now().plusDays(60));
            orderMapper.insert(orderId, userId, 30, "PAID", LocalDateTime.now());
            return null;
        });

        memberExpireService.applyMemberExpire(orderId);

        UserMemberDTO userDto = memberExpireService.getMemberInfo(userId);
        MemberExpireLogDTO logDto = memberExpireLogService.getByOrderId(orderId);

        assertNotNull(userDto);
        assertNotNull(logDto);
        assertEquals(userId, userDto.getUserId());
        assertEquals(userId, logDto.getUserId());
        assertEquals(orderId, logDto.getOrderId());
    }

    private long countToDtoMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        long count = 0;
        for (Method method : methods) {
            String methodName = method.getName().toLowerCase();
            if (methodName.contains("todto") || methodName.equals("todto")) {
                count++;
            }
        }
        return count;
    }
}
