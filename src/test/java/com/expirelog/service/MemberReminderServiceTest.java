package com.expirelog.service;

import com.expirelog.entity.MemberReminder;
import com.expirelog.entity.UserReminderSetting;
import com.expirelog.enums.ReminderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("会员到期提醒功能测试")
class MemberReminderServiceTest extends BaseIntegrationTest {

    @Autowired
    private MemberReminderService reminderService;

    @Autowired
    private UserReminderSettingService settingService;

    @Autowired
    private com.expirelog.mapper.MemberReminderMapper reminderMapper;

    @Autowired
    private com.expirelog.mapper.UserReminderSettingMapper settingMapper;

    private long userId;

    @BeforeEach
    void setupTestData() {
        userId = nextUserId();
    }

    @Test
    @DisplayName("测试扫描到期用户并创建提醒记录")
    void testScanAndCreateReminders() {
        long userId1 = nextUserId();
        long userId2 = nextUserId();

        LocalDate scanDate = LocalDate.now().plusDays(7);
        LocalDateTime expireTime1 = scanDate.atTime(10, 0);
        LocalDateTime expireTime2 = scanDate.atTime(14, 0);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId1, expireTime1);
            memberMapper.insert(userId2, expireTime2);
            return null;
        });

        int created = reminderService.scanAndCreateReminders(scanDate);

        assertTrue(created >= 2, "应该创建至少 2 条提醒记录");

        List<MemberReminder> reminders1 = reminderMapper.selectByUserId(userId1, 0, 10);
        List<MemberReminder> reminders2 = reminderMapper.selectByUserId(userId2, 0, 10);

        assertFalse(reminders1.isEmpty(), "用户1应该有提醒记录");
        assertFalse(reminders2.isEmpty(), "用户2应该有提醒记录");

        for (MemberReminder r : reminders1) {
            assertEquals(ReminderStatus.PENDING.getCode(), r.getStatus());
        }
    }

    @Test
    @DisplayName("测试避免重复发送：同一用户同一时间同一渠道只创建一条记录")
    void testAvoidDuplicateReminders() {
        LocalDate scanDate = LocalDate.now().plusDays(7);
        LocalDateTime expireTime = scanDate.atTime(10, 0);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, expireTime);
            return null;
        });

        int created1 = reminderService.scanAndCreateReminders(scanDate);
        int created2 = reminderService.scanAndCreateReminders(scanDate);

        assertTrue(created1 > 0, "第一次应该创建提醒记录");
        assertEquals(0, created2, "第二次不应该创建重复的提醒记录");

        List<MemberReminder> reminders = reminderMapper.selectByUserId(userId, 0, 10);
        assertTrue(reminders.size() <= 2, "每个渠道最多一条记录");
    }

    @Test
    @DisplayName("测试用户关闭提醒：关闭后不创建提醒记录")
    void testUserDisableReminder() {
        LocalDate scanDate = LocalDate.now().plusDays(7);
        LocalDateTime expireTime = scanDate.atTime(10, 0);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, expireTime);
            return null;
        });

        settingService.enableReminder(userId, false);

        int created = reminderService.scanAndCreateReminders(scanDate);

        List<MemberReminder> reminders = reminderMapper.selectByUserId(userId, 0, 10);
        assertTrue(reminders.isEmpty() || created == 0, "关闭提醒后不应该创建提醒记录");
    }

    @Test
    @DisplayName("测试用户渠道选择：只发送用户选择的渠道")
    void testUserChannelSelection() {
        LocalDate scanDate = LocalDate.now().plusDays(7);
        LocalDateTime expireTime = scanDate.atTime(10, 0);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, expireTime);
            return null;
        });

        settingService.saveSetting(userId, true, List.of("SMS"), 7);

        int created = reminderService.scanAndCreateReminders(scanDate);

        List<MemberReminder> reminders = reminderMapper.selectByUserId(userId, 0, 10);

        assertEquals(1, reminders.size(), "应该只创建短信渠道的提醒记录");
        assertEquals("SMS", reminders.get(0).getChannel());
    }

    @Test
    @DisplayName("测试发送待处理提醒")
    void testSendPendingReminders() {
        LocalDate scanDate = LocalDate.now().plusDays(7);
        LocalDateTime expireTime = scanDate.atTime(10, 0);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, expireTime);
            return null;
        });

        reminderService.scanAndCreateReminders(scanDate);

        List<MemberReminder> pendingBefore = reminderMapper.selectByUserId(userId, 0, 10);
        for (MemberReminder r : pendingBefore) {
            assertEquals(ReminderStatus.PENDING.getCode(), r.getStatus());
        }

        int sent = reminderService.sendPendingReminders();

        List<MemberReminder> sentReminders = reminderMapper.selectByUserId(userId, 0, 10);
        for (MemberReminder r : sentReminders) {
            assertEquals(ReminderStatus.SENT.getCode(), r.getStatus());
        }
    }

    @Test
    @DisplayName("测试用户提醒设置保存和查询")
    void testUserReminderSetting() {
        settingService.saveSetting(userId, true, List.of("SMS", "EMAIL"), 14);

        UserReminderSetting setting = settingMapper.selectByUserId(userId);

        assertNotNull(setting);
        assertEquals(userId, setting.getUserId());
        assertTrue(setting.isEnabled());
        assertEquals("SMS,EMAIL", setting.getChannels());
        assertEquals(14, setting.getDaysBeforeExpire());

        assertTrue(settingService.isEnabled(userId));
        assertTrue(settingService.getChannels(userId).contains("SMS"));
        assertTrue(settingService.getChannels(userId).contains("EMAIL"));
        assertEquals(14, settingService.getDaysBeforeExpire(userId));
    }

    @Test
    @DisplayName("测试用户提醒设置默认值")
    void testUserReminderSettingDefaultValues() {
        long newUserId = nextUserId();

        UserReminderSetting setting = settingMapper.selectByUserId(newUserId);
        assertNull(setting, "新用户没有设置记录");

        assertTrue(settingService.isEnabled(newUserId), "默认启用提醒");
        assertTrue(settingService.getChannels(newUserId).size() >= 1, "默认有渠道配置");
        assertTrue(settingService.getDaysBeforeExpire(newUserId) > 0, "默认有提前天数配置");
    }

    @Test
    @DisplayName("测试分页扫描大量用户")
    void testPaginationScan() {
        int userCount = 150;
        LocalDate scanDate = LocalDate.now().plusDays(7);
        LocalDateTime expireTime = scanDate.atTime(12, 0);

        for (int i = 0; i < userCount; i++) {
            long uid = nextUserId();
            transactionTemplate.execute(status -> {
                memberMapper.insert(uid, expireTime);
                return null;
            });
        }

        int totalCreated = reminderService.scanAndCreateReminders(scanDate);

        assertTrue(totalCreated >= userCount, "应该扫描并创建所有用户的提醒记录");
    }

    @Test
    @DisplayName("测试查询提醒记录")
    void testGetRemindersByUserId() {
        LocalDate scanDate = LocalDate.now().plusDays(7);
        LocalDateTime expireTime = scanDate.atTime(10, 0);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, expireTime);
            return null;
        });

        reminderService.scanAndCreateReminders(scanDate);

        List<MemberReminder> page1 = reminderService.getRemindersByUserId(userId, 1, 10);
        int total = reminderService.countRemindersByUserId(userId);

        assertFalse(page1.isEmpty(), "应该有提醒记录");
        assertTrue(total > 0, "应该有记录数量");
    }

    @Test
    @DisplayName("测试不影响现有功能：原有会员权益处理正常")
    void testExistingFunctionalityNotAffected() {
        long orderId = nextOrderId();
        int durationDays = 30;
        LocalDateTime initialExpire = LocalDateTime.now().plusDays(10);

        transactionTemplate.execute(status -> {
            memberMapper.insert(userId, initialExpire);
            orderMapper.insert(orderId, userId, durationDays, "PAID", LocalDateTime.now());
            return null;
        });

        memberExpireService.applyMemberExpire(orderId);

        LocalDateTime finalExpireTime = transactionTemplate.execute(status -> {
            return memberMapper.selectByUserId(userId).getExpireTime();
        });

        LocalDateTime expected = initialExpire.plusDays(durationDays);
        assertTrue(finalExpireTime.isEqual(expected) || finalExpireTime.isAfter(expected.minusSeconds(1)),
                "原有功能应该正常工作");
    }
}
