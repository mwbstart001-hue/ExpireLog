package com.expirelog.service;

import com.expirelog.entity.MemberReminder;
import com.expirelog.entity.UserReminderSetting;
import com.expirelog.enums.ReminderChannel;
import com.expirelog.enums.ReminderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("会员到期提醒功能 - 完整端到端测试")
class MemberReminderE2ETest extends BaseIntegrationTest {

    @Autowired
    private MemberReminderService reminderService;

    @Autowired
    private UserReminderSettingService settingService;

    @Autowired
    private com.expirelog.mapper.MemberReminderMapper reminderMapper;

    @Autowired
    private com.expirelog.mapper.UserReminderSettingMapper settingMapper;

    @Nested
    @DisplayName("场景一：用户设置提醒配置")
    class UserSettingTest {

        private long userId;

        @BeforeEach
        void setup() {
            userId = nextUserId();
        }

        @Test
        @DisplayName("测试1.1：新用户默认启用提醒，使用默认配置")
        void testNewUserDefaultSettings() {
            assertTrue(settingService.isEnabled(userId), "新用户默认应该启用提醒");
            List<String> channels = settingService.getChannels(userId);
            assertTrue(channels.contains("SMS") || channels.contains("EMAIL"), "默认应该有至少一个渠道");
            assertEquals(7, settingService.getDaysBeforeExpire(userId), "默认提前7天提醒");
        }

        @Test
        @DisplayName("测试1.2：用户自定义提醒设置 - 启用所有渠道")
        void testCustomSettings_AllChannels() {
            settingService.saveSetting(userId, true, List.of("SMS", "EMAIL"), 14);

            UserReminderSetting setting = settingMapper.selectByUserId(userId);
            assertNotNull(setting);
            assertTrue(setting.isEnabled());
            assertEquals("SMS,EMAIL", setting.getChannels());
            assertEquals(14, setting.getDaysBeforeExpire());
        }

        @Test
        @DisplayName("测试1.3：用户自定义提醒设置 - 仅短信渠道")
        void testCustomSettings_SmsOnly() {
            settingService.saveSetting(userId, true, List.of("SMS"), 3);

            List<String> channels = settingService.getChannels(userId);
            assertEquals(1, channels.size());
            assertEquals("SMS", channels.get(0));
        }

        @Test
        @DisplayName("测试1.4：用户关闭提醒")
        void testDisableReminder() {
            settingService.enableReminder(userId, false);
            assertFalse(settingService.isEnabled(userId));

            settingService.enableReminder(userId, true);
            assertTrue(settingService.isEnabled(userId));
        }

        @Test
        @DisplayName("测试1.5：用户更新提醒设置")
        void testUpdateSettings() {
            settingService.saveSetting(userId, true, List.of("SMS"), 7);

            settingService.saveSetting(userId, true, List.of("EMAIL"), 30);

            UserReminderSetting setting = settingMapper.selectByUserId(userId);
            assertEquals("EMAIL", setting.getChannels());
            assertEquals(30, setting.getDaysBeforeExpire());
        }
    }

    @Nested
    @DisplayName("场景二：扫描到期用户并创建提醒记录")
    class ScanAndCreateReminderTest {

        @Test
        @DisplayName("测试2.1：扫描到期时间在范围内的用户")
        void testScanExpiringUsers_InRange() {
            long userId1 = nextUserId();
            long userId2 = nextUserId();

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime1 = scanDate.atTime(9, 0);
            LocalDateTime expireTime2 = scanDate.atTime(18, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId1, expireTime1);
                memberMapper.insert(userId2, expireTime2);
                return null;
            });

            int created = reminderService.scanAndCreateReminders(scanDate);

            assertTrue(created >= 2, "应该创建至少2条提醒记录");

            List<MemberReminder> reminders1 = reminderMapper.selectByUserId(userId1, 0, 10);
            List<MemberReminder> reminders2 = reminderMapper.selectByUserId(userId2, 0, 10);

            assertFalse(reminders1.isEmpty(), "用户1应该有提醒记录");
            assertFalse(reminders2.isEmpty(), "用户2应该有提醒记录");

            for (MemberReminder r : reminders1) {
                assertEquals(ReminderStatus.PENDING.getCode(), r.getStatus());
            }
        }

        @Test
        @DisplayName("测试2.2：扫描到期时间不在范围内的用户 - 不应创建提醒")
        void testScanExpiringUsers_OutOfRange() {
            long userId1 = nextUserId();
            long userId2 = nextUserId();

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime1 = scanDate.minusDays(1).atTime(9, 0);
            LocalDateTime expireTime2 = scanDate.plusDays(1).atTime(9, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId1, expireTime1);
                memberMapper.insert(userId2, expireTime2);
                return null;
            });

            int created = reminderService.scanAndCreateReminders(scanDate);

            List<MemberReminder> reminders1 = reminderMapper.selectByUserId(userId1, 0, 10);
            List<MemberReminder> reminders2 = reminderMapper.selectByUserId(userId2, 0, 10);

            boolean user1InRange = reminders1.stream()
                    .anyMatch(r -> r.getRemindTime().toLocalDate().equals(scanDate));
            boolean user2InRange = reminders2.stream()
                    .anyMatch(r -> r.getRemindTime().toLocalDate().equals(scanDate));

            assertFalse(user1InRange, "到期时间在扫描日期前的用户不应被扫描");
            assertFalse(user2InRange, "到期时间在扫描日期后的用户不应被扫描");
        }

        @Test
        @DisplayName("测试2.3：已关闭提醒的用户 - 不应创建提醒记录")
        void testScan_DisabledUser_NoReminder() {
            long userId = nextUserId();

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(10, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, expireTime);
                return null;
            });

            settingService.enableReminder(userId, false);

            int created = reminderService.scanAndCreateReminders(scanDate);

            List<MemberReminder> reminders = reminderMapper.selectByUserId(userId, 0, 10);

            boolean hasReminderForScanDate = reminders.stream()
                    .anyMatch(r -> r.getRemindTime().toLocalDate().equals(scanDate));

            assertFalse(hasReminderForScanDate, "已关闭提醒的用户不应创建提醒记录");
        }

        @Test
        @DisplayName("测试2.4：用户选择特定渠道 - 只创建选择渠道的提醒")
        void testScan_SelectedChannelsOnly() {
            long userId = nextUserId();

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(10, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, expireTime);
                return null;
            });

            settingService.saveSetting(userId, true, List.of("SMS"), 7);

            int created = reminderService.scanAndCreateReminders(scanDate);

            List<MemberReminder> reminders = reminderMapper.selectByUserId(userId, 0, 10);

            assertEquals(1, reminders.size(), "应该只创建一个短信渠道的提醒");
            assertEquals("SMS", reminders.get(0).getChannel());
        }
    }

    @Nested
    @DisplayName("场景三：避免重复发送 - 幂等性保证")
    class IdempotencyTest {

        @Test
        @DisplayName("测试3.1：同一用户同一时间同一渠道 - 只创建一条记录")
        void testDuplicateReminder_SameUserSameTimeSameChannel() {
            long userId = nextUserId();

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(10, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, expireTime);
                return null;
            });

            int created1 = reminderService.scanAndCreateReminders(scanDate);
            int created2 = reminderService.scanAndCreateReminders(scanDate);
            int created3 = reminderService.scanAndCreateReminders(scanDate);

            assertTrue(created1 > 0, "第一次应该创建提醒记录");
            assertEquals(0, created2, "第二次不应该创建重复记录");
            assertEquals(0, created3, "第三次不应该创建重复记录");

            List<MemberReminder> reminders = reminderMapper.selectByUserId(userId, 0, 10);

            long smsCount = reminders.stream().filter(r -> "SMS".equals(r.getChannel())).count();
            long emailCount = reminders.stream().filter(r -> "EMAIL".equals(r.getChannel())).count();

            assertTrue(smsCount <= 1, "短信渠道最多一条记录");
            assertTrue(emailCount <= 1, "邮件渠道最多一条记录");
        }

        @Test
        @DisplayName("测试3.2：同一用户不同时间 - 可以创建多条记录")
        void testDifferentTime_CanCreateMultiple() {
            long userId = nextUserId();

            LocalDate scanDate1 = LocalDate.now().plusDays(7);
            LocalDate scanDate2 = LocalDate.now().plusDays(14);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, scanDate1.atTime(10, 0));
                return null;
            });

            int created1 = reminderService.scanAndCreateReminders(scanDate1);

            transactionTemplate.execute(status -> {
                memberMapper.updateExpireTime(userId, scanDate2.atTime(10, 0));
                return null;
            });

            int created2 = reminderService.scanAndCreateReminders(scanDate2);

            assertTrue(created1 > 0, "第一次应该创建提醒记录");
            assertTrue(created2 > 0, "第二次（不同时间）也应该创建提醒记录");

            List<MemberReminder> reminders = reminderMapper.selectByUserId(userId, 0, 20);
            assertTrue(reminders.size() >= 2, "应该有至少2条不同时间的提醒记录");
        }
    }

    @Nested
    @DisplayName("场景四：发送提醒消息")
    class SendReminderTest {

        @Test
        @DisplayName("测试4.1：发送待处理提醒 - 状态更新为已发送")
        void testSendPendingReminders_StatusUpdates() {
            long userId = nextUserId();

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(10, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, expireTime);
                return null;
            });

            reminderService.scanAndCreateReminders(scanDate);

            List<MemberReminder> beforeSend = reminderMapper.selectByUserId(userId, 0, 10);
            for (MemberReminder r : beforeSend) {
                assertEquals(ReminderStatus.PENDING.getCode(), r.getStatus(), "发送前应该是待处理状态");
            }

            int sent = reminderService.sendPendingReminders();

            List<MemberReminder> afterSend = reminderMapper.selectByUserId(userId, 0, 10);
            for (MemberReminder r : afterSend) {
                assertEquals(ReminderStatus.SENT.getCode(), r.getStatus(), "发送后应该是已发送状态");
            }

            assertTrue(sent > 0, "应该发送了至少一条提醒");
        }

        @Test
        @DisplayName("测试4.2：已发送的提醒 - 不会重复发送")
        void testSentReminders_NotSentAgain() {
            long userId = nextUserId();

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(10, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, expireTime);
                return null;
            });

            reminderService.scanAndCreateReminders(scanDate);

            int sent1 = reminderService.sendPendingReminders();
            int sent2 = reminderService.sendPendingReminders();

            assertTrue(sent1 > 0, "第一次应该发送提醒");
            assertEquals(0, sent2, "第二次不应该发送已发送的提醒");
        }
    }

    @Nested
    @DisplayName("场景五：完整端到端流程")
    class FullE2ETest {

        @Test
        @DisplayName("测试5.1：完整流程 - 设置 → 扫描 → 发送 → 查询")
        void testFullE2EFlow() {
            long userId = nextUserId();

            settingService.saveSetting(userId, true, List.of("SMS", "EMAIL"), 7);

            UserReminderSetting setting = settingMapper.selectByUserId(userId);
            assertNotNull(setting);
            assertTrue(setting.isEnabled());
            assertTrue(setting.getChannels().contains("SMS"));
            assertTrue(setting.getChannels().contains("EMAIL"));

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(12, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, expireTime);
                return null;
            });

            int created = reminderService.scanAndCreateReminders(scanDate);

            List<MemberReminder> pending = reminderMapper.selectByUserId(userId, 0, 10);
            assertTrue(created >= 2, "应该创建至少2条提醒记录（两个渠道）");
            for (MemberReminder r : pending) {
                assertEquals(ReminderStatus.PENDING.getCode(), r.getStatus());
            }

            int sent = reminderService.sendPendingReminders();

            List<MemberReminder> sentReminders = reminderMapper.selectByUserId(userId, 0, 10);
            assertEquals(2, sent, "应该发送2条提醒");
            for (MemberReminder r : sentReminders) {
                assertEquals(ReminderStatus.SENT.getCode(), r.getStatus());
            }

            List<MemberReminder> queried = reminderService.getRemindersByUserId(userId, 1, 10);
            int total = reminderService.countRemindersByUserId(userId);

            assertFalse(queried.isEmpty(), "应该能查询到提醒记录");
            assertEquals(2, total, "应该有2条提醒记录");
        }

        @Test
        @DisplayName("测试5.2：完整流程 - 用户关闭提醒后不发送")
        void testFullE2EFlow_Disabled() {
            long userId = nextUserId();

            settingService.saveSetting(userId, false, List.of("SMS", "EMAIL"), 7);

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(12, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, expireTime);
                return null;
            });

            int created = reminderService.scanAndCreateReminders(scanDate);

            List<MemberReminder> reminders = reminderMapper.selectByUserId(userId, 0, 10);

            boolean hasReminderForScanDate = reminders.stream()
                    .anyMatch(r -> r.getRemindTime().toLocalDate().equals(scanDate));

            assertFalse(hasReminderForScanDate, "关闭提醒的用户不应被扫描");
        }

        @Test
        @DisplayName("测试5.3：完整流程 - 分页处理大批量用户")
        void testFullE2EFlow_BatchProcessing() {
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
            int totalSent = reminderService.sendPendingReminders();

            assertTrue(totalCreated >= userCount * 2, "应该为每个用户创建至少2条提醒（两个渠道）");
            assertTrue(totalSent >= userCount * 2, "应该发送至少2条提醒");
        }
    }

    @Nested
    @DisplayName("场景六：边界情况测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("测试6.1：用户不存在 - 不影响其他用户")
        void testNonExistentUser() {
            long userId1 = nextUserId();
            long nonExistentUserId = 999999L;

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(10, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId1, expireTime);
                return null;
            });

            assertFalse(settingService.isEnabled(nonExistentUserId), "不存在的用户应该使用默认值但不创建记录");

            int created = reminderService.scanAndCreateReminders(scanDate);

            List<MemberReminder> reminders = reminderMapper.selectByUserId(userId1, 0, 10);
            assertFalse(reminders.isEmpty(), "存在的用户应该正常处理");
        }

        @Test
        @DisplayName("测试6.2：用户选择空渠道 - 使用默认渠道")
        void testEmptyChannels_UseDefault() {
            long userId = nextUserId();

            settingService.saveSetting(userId, true, List.of(), 7);

            List<String> channels = settingService.getChannels(userId);
            assertNotNull(channels);
            assertFalse(channels.isEmpty(), "空渠道应该使用默认渠道");
        }

        @Test
        @DisplayName("测试6.3：用户选择无效渠道 - 正常处理")
        void testInvalidChannels() {
            long userId = nextUserId();

            settingService.saveSetting(userId, true, List.of("INVALID_CHANNEL"), 7);

            LocalDate scanDate = LocalDate.now().plusDays(7);
            LocalDateTime expireTime = scanDate.atTime(10, 0);

            transactionTemplate.execute(status -> {
                memberMapper.insert(userId, expireTime);
                return null;
            });

            int created = reminderService.scanAndCreateReminders(scanDate);
        }
    }
}
