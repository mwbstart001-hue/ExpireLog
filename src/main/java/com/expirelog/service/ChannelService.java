package com.expirelog.service;

import com.expirelog.entity.UserMember;

public interface ChannelService {

    String getChannel();

    boolean send(UserMember member, String message);
}
