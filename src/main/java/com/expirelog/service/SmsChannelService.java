package com.expirelog.service;

import com.expirelog.entity.UserMember;
import com.expirelog.enums.ReminderChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SmsChannelService implements ChannelService {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelService.class);

    @Override
    public String getChannel() {
        return ReminderChannel.SMS.getCode();
    }

    @Override
    public boolean send(UserMember member, String message) {
        log.info("[短信渠道] 发送提醒短信给用户 {}: {}", member.getUserId(), message);
        return true;
    }
}
