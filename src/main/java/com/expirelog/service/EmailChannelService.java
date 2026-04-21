package com.expirelog.service;

import com.expirelog.entity.UserMember;
import com.expirelog.enums.ReminderChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailChannelService implements ChannelService {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelService.class);

    @Override
    public String getChannel() {
        return ReminderChannel.EMAIL.getCode();
    }

    @Override
    public boolean send(UserMember member, String message) {
        log.info("[邮件渠道] 发送提醒邮件给用户 {}: {}", member.getUserId(), message);
        return true;
    }
}
