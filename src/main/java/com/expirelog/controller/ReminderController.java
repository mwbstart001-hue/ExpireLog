package com.expirelog.controller;

import com.expirelog.dto.MemberReminderDTO;
import com.expirelog.dto.PageResult;
import com.expirelog.dto.UserReminderSettingDTO;
import com.expirelog.entity.MemberReminder;
import com.expirelog.entity.UserReminderSetting;
import com.expirelog.service.MemberReminderService;
import com.expirelog.service.UserReminderSettingService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/member/reminder")
public class ReminderController {

    private final MemberReminderService reminderService;
    private final UserReminderSettingService settingService;

    public ReminderController(MemberReminderService reminderService,
                               UserReminderSettingService settingService) {
        this.reminderService = reminderService;
        this.settingService = settingService;
    }

    @GetMapping("/{userId}/logs")
    public PageResult<MemberReminderDTO> getReminderLogs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        int validPage = Math.max(page, 1);
        int validSize = Math.max(1, Math.min(size, 100));

        List<MemberReminder> reminders = reminderService.getRemindersByUserId(userId, validPage, validSize);
        int total = reminderService.countRemindersByUserId(userId);

        List<MemberReminderDTO> dtos = reminders.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResult<>(dtos, total, validPage, validSize);
    }

    @GetMapping("/setting/{userId}")
    public UserReminderSettingDTO getSetting(@PathVariable Long userId) {
        UserReminderSetting setting = settingService.getByUserId(userId);
        if (setting == null) {
            UserReminderSettingDTO dto = new UserReminderSettingDTO();
            dto.setUserId(userId);
            dto.setEnabled(settingService.isEnabled(userId));
            dto.setChannels(settingService.getChannels(userId));
            dto.setDaysBeforeExpire(settingService.getDaysBeforeExpire(userId));
            return dto;
        }
        return toDTO(setting);
    }

    @PostMapping("/setting/{userId}")
    public void saveSetting(@PathVariable Long userId,
                            @RequestBody UserReminderSettingDTO request) {
        settingService.saveSetting(userId, request.isEnabled(), request.getChannels(), request.getDaysBeforeExpire());
    }

    @PostMapping("/setting/{userId}/enable")
    public void enableReminder(@PathVariable Long userId,
                               @RequestParam boolean enabled) {
        settingService.enableReminder(userId, enabled);
    }

    private MemberReminderDTO toDTO(MemberReminder entity) {
        if (entity == null) {
            return null;
        }
        MemberReminderDTO dto = new MemberReminderDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setRemindTime(entity.getRemindTime());
        dto.setChannel(entity.getChannel());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private UserReminderSettingDTO toDTO(UserReminderSetting entity) {
        if (entity == null) {
            return null;
        }
        UserReminderSettingDTO dto = new UserReminderSettingDTO();
        dto.setUserId(entity.getUserId());
        dto.setEnabled(entity.isEnabled());
        if (entity.getChannels() != null) {
            dto.setChannels(Arrays.asList(entity.getChannels().split(",")));
        }
        dto.setDaysBeforeExpire(entity.getDaysBeforeExpire());
        return dto;
    }
}
