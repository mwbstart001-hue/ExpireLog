package com.expirelog.service;

import com.expirelog.config.ReminderProperties;
import com.expirelog.entity.UserReminderSetting;
import com.expirelog.mapper.UserReminderSettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Service
public class UserReminderSettingService {

    private static final Logger log = LoggerFactory.getLogger(UserReminderSettingService.class);

    private final UserReminderSettingMapper settingMapper;
    private final ReminderProperties properties;

    public UserReminderSettingService(UserReminderSettingMapper settingMapper,
                                       ReminderProperties properties) {
        this.settingMapper = settingMapper;
        this.properties = properties;
    }

    public UserReminderSetting getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return settingMapper.selectByUserId(userId);
    }

    @Transactional
    public void saveSetting(Long userId, boolean enabled, List<String> channels, Integer daysBeforeExpire) {
        String channelStr = channels != null ? String.join(",", channels) : null;
        if (!StringUtils.hasText(channelStr)) {
            channelStr = String.join(",", properties.getDefaultChannels());
        }
        settingMapper.upsert(userId, enabled, channelStr, daysBeforeExpire != null ? daysBeforeExpire : properties.getDaysBeforeExpire());
        log.info("保存用户提醒设置: userId={}, enabled={}, channels={}, daysBeforeExpire={}",
                userId, enabled, channelStr, daysBeforeExpire);
    }

    @Transactional
    public void enableReminder(Long userId, boolean enabled) {
        UserReminderSetting existing = settingMapper.selectByUserId(userId);
        if (existing != null) {
            saveSetting(userId, enabled, Arrays.asList(existing.getChannels().split(",")), existing.getDaysBeforeExpire());
        } else {
            saveSetting(userId, enabled, properties.getDefaultChannels(), properties.getDaysBeforeExpire());
        }
    }

    public boolean isEnabled(Long userId) {
        UserReminderSetting setting = getByUserId(userId);
        if (setting == null) {
            return properties.isEnabled();
        }
        return setting.isEnabled();
    }

    public List<String> getChannels(Long userId) {
        UserReminderSetting setting = getByUserId(userId);
        if (setting == null || !StringUtils.hasText(setting.getChannels())) {
            return properties.getDefaultChannels();
        }
        return Arrays.asList(setting.getChannels().split(","));
    }

    public int getDaysBeforeExpire(Long userId) {
        UserReminderSetting setting = getByUserId(userId);
        if (setting == null || setting.getDaysBeforeExpire() == null) {
            return properties.getDaysBeforeExpire();
        }
        return setting.getDaysBeforeExpire();
    }
}
