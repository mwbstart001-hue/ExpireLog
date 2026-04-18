package com.expirelog.service;

import com.expirelog.config.MemberExpireProperties;
import com.expirelog.entity.MemberOrder;
import com.expirelog.entity.UserMember;
import com.expirelog.enums.MemberAccumulationStrategy;
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
    private final MemberExpireProperties properties;

    public MemberExpireService(MemberOrderMapper orderMapper,
                                MemberExpireLogMapper expireLogMapper,
                                UserMemberMapper memberMapper,
                                MemberExpireProperties properties) {
        this.orderMapper = orderMapper;
        this.expireLogMapper = expireLogMapper;
        this.memberMapper = memberMapper;
        this.properties = properties;
    }

    @Transactional
    public void applyMemberExpire(Long orderId) {

        Assert.notNull(orderId, "orderId must not be null");

        log.info("开始处理会员权益订单, orderId={}, strategy={}", orderId, properties.getStrategy());

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

        UserMember member = memberMapper.selectForUpdate(userId);

        LocalDateTime newExpire = calculateNewExpireTime(member, durationDays, now);

        try {
            expireLogMapper.insert(userId, durationDays, orderId, now);
        } catch (DuplicateKeyException e) {
            log.info("订单已被并发处理, orderId={}", orderId);
            return;
        }

        if (member == null) {
            log.info("用户首次购买会员, userId={}, newExpire={}", userId, newExpire);
            memberMapper.insert(userId, newExpire);
        } else {
            log.info("更新会员到期时间: userId={}, oldExpire={}, newExpire={}",
                    userId, member.getExpireTime(), newExpire);
            memberMapper.updateExpireTime(userId, newExpire);
        }

        log.info("处理会员权益订单完成, orderId={}", orderId);
    }

    public LocalDateTime calculateNewExpireTime(UserMember member, int durationDays, LocalDateTime now) {

        if (member == null) {
            return now.plusDays(durationDays);
        }

        LocalDateTime oldExpire = member.getExpireTime();
        MemberAccumulationStrategy strategy = properties.getStrategy();

        log.debug("计算到期时间: strategy={}, oldExpire={}, now={}, durationDays={}",
                strategy, oldExpire, now, durationDays);

        LocalDateTime baseTime;

        switch (strategy) {
            case STRICT:
                baseTime = calculateBaseTimeStrict(oldExpire);
                break;
            case GRACE:
                baseTime = calculateBaseTimeGrace(oldExpire, now, properties.getGraceDays());
                break;
            case RESET:
            default:
                baseTime = calculateBaseTimeReset(oldExpire, now);
                break;
        }

        LocalDateTime newExpire = baseTime.plusDays(durationDays);
        log.debug("计算结果: baseTime={}, newExpire={}", baseTime, newExpire);

        return newExpire;
    }

    private LocalDateTime calculateBaseTimeStrict(LocalDateTime oldExpire) {
        return oldExpire;
    }

    private LocalDateTime calculateBaseTimeGrace(LocalDateTime oldExpire, LocalDateTime now, int graceDays) {
        LocalDateTime graceEndTime = oldExpire.plusDays(graceDays);
        if (now.isBefore(graceEndTime) || now.isEqual(graceEndTime)) {
            return oldExpire;
        } else {
            return now;
        }
    }

    private LocalDateTime calculateBaseTimeReset(LocalDateTime oldExpire, LocalDateTime now) {
        return oldExpire.isAfter(now) ? oldExpire : now;
    }
}
