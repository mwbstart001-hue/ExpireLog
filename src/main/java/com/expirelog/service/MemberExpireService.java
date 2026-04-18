package com.expirelog.service;

import com.expirelog.entity.MemberOrder;
import com.expirelog.entity.UserMember;
import com.expirelog.mapper.MemberExpireLogMapper;
import com.expirelog.mapper.MemberOrderMapper;
import com.expirelog.mapper.UserMemberMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MemberExpireService {

    private final MemberOrderMapper orderMapper;
    private final MemberExpireLogMapper expireLogMapper;
    private final UserMemberMapper memberMapper;

    public MemberExpireService(MemberOrderMapper orderMapper,
                                MemberExpireLogMapper expireLogMapper,
                                UserMemberMapper memberMapper) {
        this.orderMapper = orderMapper;
        this.expireLogMapper = expireLogMapper;
        this.memberMapper = memberMapper;
    }

    @Transactional
    public void applyMemberExpire(Long orderId) {

        MemberOrder order = orderMapper.selectPaid(orderId);
        if (order == null) {
            return;
        }

        if (expireLogMapper.existsByOrderId(orderId)) {
            return;
        }

        UserMember member = memberMapper.selectForUpdate(order.getUserId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseTime = member.getExpireTime().isAfter(now)
                ? member.getExpireTime()
                : now;

        LocalDateTime newExpire = baseTime.plusDays(order.getDurationDays());

        try {
            expireLogMapper.insert(
                    order.getUserId(),
                    order.getDurationDays(),
                    orderId,
                    now
            );
        } catch (DuplicateKeyException e) {
            return;
        }

        memberMapper.updateExpireTime(
                order.getUserId(),
                newExpire
        );
    }
}
