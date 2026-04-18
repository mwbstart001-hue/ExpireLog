package com.expirelog.service;

import com.expirelog.entity.MemberOrder;
import com.expirelog.entity.UserMember;
import com.expirelog.mapper.MemberExpireLogMapper;
import com.expirelog.mapper.MemberOrderMapper;
import com.expirelog.mapper.UserMemberMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Service
public class MemberExpireService {

    private static final Logger log = LoggerFactory.getLogger(MemberExpireService.class);

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

        Assert.notNull(orderId, "orderId must not be null");

        log.info("开始处理会员权益订单, orderId={}", orderId);

        MemberOrder order = orderMapper.selectPaid(orderId);
        if (order == null) {
            log.warn("订单不存在或未支付, orderId={}", orderId);
            return;
        }

        Assert.notNull(order.getUserId(), "order userId must not be null");
        Assert.notNull(order.getDurationDays(), "order durationDays must not be null");
        Assert.isTrue(order.getDurationDays() > 0, "durationDays must be positive");

        Long userId = order.getUserId();
        int durationDays = order.getDurationDays();

        log.info("订单信息: orderId={}, userId={}, durationDays={}", orderId, userId, durationDays);

        if (expireLogMapper.existsByOrderId(orderId)) {
            log.info("订单已处理过, 跳过, orderId={}", orderId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseTime;
        LocalDateTime newExpire;

        UserMember member = memberMapper.selectForUpdate(userId);

        if (member == null) {
            log.info("用户首次购买会员, userId={}", userId);
            baseTime = now;
            newExpire = baseTime.plusDays(durationDays);

            try {
                expireLogMapper.insert(userId, durationDays, orderId, now);
            } catch (DuplicateKeyException e) {
                log.info("订单已被并发处理, orderId={}", orderId);
                return;
            }

            memberMapper.insert(userId, newExpire);
            log.info("用户首次购买会员完成, userId={}, newExpire={}", userId, newExpire);
        } else {
            LocalDateTime oldExpire = member.getExpireTime();
            log.info("用户当前会员到期时间: userId={}, oldExpire={}", userId, oldExpire);

            baseTime = oldExpire.isAfter(now) ? oldExpire : now;
            newExpire = baseTime.plusDays(durationDays);

            log.info("到期时间计算: baseTime={} (max({}, {})), newExpire={}",
                    baseTime, oldExpire, now, newExpire);

            try {
                expireLogMapper.insert(userId, durationDays, orderId, now);
            } catch (DuplicateKeyException e) {
                log.info("订单已被并发处理, orderId={}", orderId);
                return;
            }

            memberMapper.updateExpireTime(userId, newExpire);
            log.info("会员到期时间更新完成, userId={}, newExpire={}", userId, newExpire);
        }

        log.info("处理会员权益订单完成, orderId={}", orderId);
    }
}
