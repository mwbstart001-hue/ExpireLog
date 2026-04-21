package com.expirelog.service;

import com.expirelog.config.ReminderProperties;
import com.expirelog.entity.MemberReminder;
import com.expirelog.entity.UserMember;
import com.expirelog.enums.ReminderStatus;
import com.expirelog.mapper.MemberReminderMapper;
import com.expirelog.mapper.UserMemberMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MemberReminderService {

    private static final Logger log = LoggerFactory.getLogger(MemberReminderService.class);

    private final UserMemberMapper userMemberMapper;
    private final MemberReminderMapper reminderMapper;
    private final UserReminderSettingService settingService;
    private final List<ChannelService> channelServices;
    private final ReminderProperties properties;

    private Map<String, ChannelService> channelServiceMap;

    public MemberReminderService(UserMemberMapper userMemberMapper,
                                  MemberReminderMapper reminderMapper,
                                  UserReminderSettingService settingService,
                                  List<ChannelService> channelServices,
                                  ReminderProperties properties) {
        this.userMemberMapper = userMemberMapper;
        this.reminderMapper = reminderMapper;
        this.settingService = settingService;
        this.channelServices = channelServices;
        this.properties = properties;
        this.channelServiceMap = channelServices.stream()
                .collect(Collectors.toMap(ChannelService::getChannel, cs -> cs));
    }

    @Transactional
    public int scanAndCreateReminders(LocalDate scanDate) {
        log.info("开始扫描到期会员, 扫描日期: {}", scanDate);

        int totalCreated = 0;
        int offset = 0;
        int batchSize = properties.getBatchSize();

        LocalDateTime scanStartTime = scanDate.atStartOfDay();
        LocalDateTime scanEndTime = scanDate.atTime(LocalTime.MAX);

        while (true) {
            List<UserMember> expiringUsers = userMemberMapper.selectExpiringUsers(
                    scanStartTime, scanEndTime, offset, batchSize);

            if (expiringUsers.isEmpty()) {
                break;
            }

            for (UserMember member : expiringUsers) {
                int created = createRemindersForMember(member, scanDate);
                totalCreated += created;
            }

            offset += batchSize;
            log.info("已扫描 {} 个用户, 创建 {} 条提醒记录", offset, totalCreated);
        }

        log.info("扫描完成, 共创建 {} 条提醒记录", totalCreated);
        return totalCreated;
    }

    private int createRemindersForMember(UserMember member, LocalDate scanDate) {
        Long userId = member.getUserId();

        if (!settingService.isEnabled(userId)) {
            log.debug("用户已关闭提醒, userId={}", userId);
            return 0;
        }

        List<String> channels = settingService.getChannels(userId);
        int created = 0;

        for (String channel : channels) {
            if (reminderMapper.existsByUserAndTimeAndChannel(userId, member.getExpireTime(), channel)) {
                log.debug("提醒记录已存在, userId={}, channel={}, expireTime={}",
                        userId, channel, member.getExpireTime());
                continue;
            }

            try {
                reminderMapper.insert(userId, member.getExpireTime(), channel, ReminderStatus.PENDING.getCode());
                created++;
                log.info("创建提醒记录: userId={}, channel={}, expireTime={}",
                        userId, channel, member.getExpireTime());
            } catch (Exception e) {
                log.warn("创建提醒记录失败, userId={}, channel={}", userId, channel, e);
            }
        }

        return created;
    }

    @Transactional
    public int sendPendingReminders() {
        log.info("开始发送待处理的提醒");

        int totalSent = 0;

        for (ChannelService channelService : channelServices) {
            String channel = channelService.getChannel();
            int sent = sendByChannel(channelService);
            totalSent += sent;
            log.info("渠道 {} 发送完成, 发送数量: {}", channel, sent);
        }

        log.info("提醒发送完成, 共发送 {} 条", totalSent);
        return totalSent;
    }

    private int sendByChannel(ChannelService channelService) {
        String channel = channelService.getChannel();
        int sent = 0;
        int offset = 0;
        int batchSize = properties.getBatchSize();

        while (true) {
            List<MemberReminder> pending = reminderMapper.selectPendingByChannel(channel, offset, batchSize);

            if (pending.isEmpty()) {
                break;
            }

            for (MemberReminder reminder : pending) {
                boolean success = sendReminder(reminder, channelService);
                if (success) {
                    reminderMapper.updateStatus(reminder.getId(), ReminderStatus.SENT.getCode());
                    sent++;
                } else {
                    reminderMapper.updateStatus(reminder.getId(), ReminderStatus.FAILED.getCode());
                }
            }

            offset += batchSize;
        }

        return sent;
    }

    private boolean sendReminder(MemberReminder reminder, ChannelService channelService) {
        try {
            UserMember member = userMemberMapper.selectByUserId(reminder.getUserId());
            if (member == null) {
                log.warn("用户不存在, userId={}", reminder.getUserId());
                return false;
            }

            String message = buildReminderMessage(member);
            boolean success = channelService.send(member, message);

            if (success) {
                log.info("提醒发送成功: userId={}, channel={}", reminder.getUserId(), channelService.getChannel());
            } else {
                log.warn("提醒发送失败: userId={}, channel={}", reminder.getUserId(), channelService.getChannel());
            }

            return success;

        } catch (Exception e) {
            log.error("发送提醒异常: userId={}", reminder.getUserId(), e);
            return false;
        }
    }

    private String buildReminderMessage(UserMember member) {
        return String.format("尊敬的用户，您的会员权益将于 %s 到期，请及时续费。",
                member.getExpireTime().toLocalDate().toString());
    }

    public List<MemberReminder> getRemindersByUserId(Long userId, int page, int size) {
        int validPage = Math.max(page, 1);
        int validSize = Math.max(1, Math.min(size, 100));
        int offset = (validPage - 1) * validSize;
        return reminderMapper.selectByUserId(userId, offset, validSize);
    }

    public int countRemindersByUserId(Long userId) {
        return reminderMapper.countByUserId(userId);
    }

    @Transactional
    public void processReminderBatch(LocalDate scanDate) {
        scanAndCreateReminders(scanDate);
        sendPendingReminders();
    }
}
