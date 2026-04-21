package com.expirelog.scheduler;

import com.expirelog.config.ReminderProperties;
import com.expirelog.service.MemberReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final MemberReminderService reminderService;
    private final ReminderProperties properties;

    public ReminderScheduler(MemberReminderService reminderService,
                             ReminderProperties properties) {
        this.reminderService = reminderService;
        this.properties = properties;
    }

    @Scheduled(cron = "${member.reminder.daily.cron:0 0 9 * * ?}")
    public void runDailyReminder() {
        if (!properties.isEnabled() || !properties.getDaily().isEnabled()) {
            log.debug("每日提醒任务已禁用");
            return;
        }

        log.info("开始执行每日提醒任务");
        LocalDate scanDate = LocalDate.now().plusDays(properties.getDaysBeforeExpire());
        reminderService.processReminderBatch(scanDate);
        log.info("每日提醒任务执行完成");
    }

    @Scheduled(cron = "${member.reminder.weekly.cron:0 0 9 ? * MON}")
    public void runWeeklyReminder() {
        if (!properties.isEnabled() || !properties.getWeekly().isEnabled()) {
            log.debug("每周提醒任务已禁用");
            return;
        }

        log.info("开始执行每周提醒任务");
        LocalDate scanDate = LocalDate.now().plusDays(properties.getDaysBeforeExpire());
        reminderService.processReminderBatch(scanDate);
        log.info("每周提醒任务执行完成");
    }

    @Scheduled(cron = "${member.reminder.monthly.cron:0 0 9 1 * ?}")
    public void runMonthlyReminder() {
        if (!properties.isEnabled() || !properties.getMonthly().isEnabled()) {
            log.debug("每月提醒任务已禁用");
            return;
        }

        log.info("开始执行每月提醒任务");
        LocalDate scanDate = LocalDate.now().plusDays(properties.getDaysBeforeExpire());
        reminderService.processReminderBatch(scanDate);
        log.info("每月提醒任务执行完成");
    }
}
