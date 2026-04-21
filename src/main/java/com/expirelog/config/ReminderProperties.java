package com.expirelog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "member.reminder")
public class ReminderProperties {

    private boolean enabled = true;
    private int daysBeforeExpire = 7;
    private int batchSize = 100;
    private List<String> defaultChannels = List.of("SMS", "EMAIL");
    private ScheduleConfig daily = new ScheduleConfig("0 0 9 * * ?");
    private ScheduleConfig weekly = new ScheduleConfig("0 0 9 ? * MON");
    private ScheduleConfig monthly = new ScheduleConfig("0 0 9 1 * ?");

    @Data
    public static class ScheduleConfig {
        private boolean enabled = true;
        private String cron;

        public ScheduleConfig() {}

        public ScheduleConfig(String cron) {
            this.cron = cron;
        }
    }
}
