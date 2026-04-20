package com.expirelog.service;

import com.expirelog.config.MemberExpireProperties;
import com.expirelog.dto.BatchApplyResult;
import com.expirelog.dto.UserMemberDTO;
import com.expirelog.entity.MemberOrder;
import com.expirelog.entity.UserMember;
import com.expirelog.enums.MemberAccumulationStrategy;
import com.expirelog.mapper.MemberExpireLogMapper;
import com.expirelog.mapper.MemberOrderMapper;
import com.expirelog.mapper.UserMemberMapper;
import com.expirelog.strategy.AccumulationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class MemberExpireService {

    private static final Logger log = LoggerFactory.getLogger(MemberExpireService.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final MemberOrderMapper orderMapper;
    private final MemberExpireLogMapper expireLogMapper;
    private final UserMemberMapper memberMapper;
    private final MemberExpireProperties properties;
    private final List<AccumulationStrategy> strategies;

    private Map<MemberAccumulationStrategy, AccumulationStrategy> strategyMap;

    public MemberExpireService(MemberOrderMapper orderMapper,
                                MemberExpireLogMapper expireLogMapper,
                                UserMemberMapper memberMapper,
                                MemberExpireProperties properties,
                                List<AccumulationStrategy> strategies) {
        this.orderMapper = orderMapper;
        this.expireLogMapper = expireLogMapper;
        this.memberMapper = memberMapper;
        this.properties = properties;
        this.strategies = strategies;
    }

    @PostConstruct
    public void initStrategyMap() {
        strategyMap = new EnumMap<>(MemberAccumulationStrategy.class);
        for (AccumulationStrategy strategy : strategies) {
            strategyMap.put(strategy.getStrategyType(), strategy);
        }
        log.info("已加载 {} 种会员叠加策略: {}", strategyMap.size(), strategyMap.keySet());
    }

    @Transactional
    public void applyMemberExpire(Long orderId) {
        doApplyMemberExpire(orderId);
    }

    @Async("memberAsyncExecutor")
    public void applyMemberExpireAsync(Long orderId) {
        log.info("异步处理会员权益订单, orderId={}", orderId);
        try {
            doApplyMemberExpire(orderId);
            log.info("异步处理会员权益订单完成, orderId={}", orderId);
        } catch (Exception e) {
            log.error("异步处理会员权益订单失败, orderId={}", orderId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApplyResult applyMemberExpireInNewTransaction(Long orderId) {
        return doApplyMemberExpire(orderId);
    }

    public BatchApplyResult applyMemberExpireBatch(List<Long> orderIds) {
        Assert.notNull(orderIds, "orderIds must not be null");
        Assert.isTrue(orderIds.size() <= MAX_BATCH_SIZE,
                "批量处理订单数不能超过 " + MAX_BATCH_SIZE);

        log.info("开始批量处理会员权益订单, 订单数={}", orderIds.size());

        BatchApplyResult result = new BatchApplyResult();

        for (Long orderId : orderIds) {
            try {
                ApplyResult applyResult = applyMemberExpireInNewTransaction(orderId);
                if (applyResult.isSuccess() || applyResult.isAlreadyProcessed()) {
                    result.addSuccess(orderId);
                } else {
                    result.addFailed(orderId, applyResult.getReason());
                }
            } catch (Exception e) {
                log.error("批量处理订单异常, orderId={}", orderId, e);
                result.addFailed(orderId, "处理异常: " + e.getMessage());
            }
        }

        log.info("批量处理完成, 成功={}, 失败={}",
                result.getSuccess().size(), result.getFailed().size());

        return result;
    }

    private ApplyResult doApplyMemberExpire(Long orderId) {
        try {
            Assert.notNull(orderId, "orderId must not be null");

            log.info("开始处理会员权益订单, orderId={}, strategy={}", orderId, properties.getStrategy());

            MemberOrder order = orderMapper.selectPaid(orderId);
            if (order == null) {
                log.warn("订单不存在或未支付, orderId={}", orderId);
                return ApplyResult.failed("订单不存在或未支付");
            }

            Assert.notNull(order.getUserId(), "order userId must not be null");
            Assert.notNull(order.getDurationDays(), "order durationDays must not be null");
            Assert.isTrue(order.getDurationDays() > 0, "durationDays must be positive");

            Long userId = order.getUserId();
            int durationDays = order.getDurationDays();

            log.info("订单信息: orderId={}, userId={}, durationDays={}", orderId, userId, durationDays);

            if (expireLogMapper.existsByOrderId(orderId)) {
                log.info("订单已处理过, 跳过, orderId={}", orderId);
                return ApplyResult.alreadyProcessed();
            }

            LocalDateTime now = LocalDateTime.now();

            memberMapper.ensureExists(userId, now);

            UserMember member = memberMapper.selectForUpdate(userId);

            LocalDateTime newExpire = calculateNewExpireTimeInternal(member, durationDays, now);

            try {
                expireLogMapper.insert(userId, durationDays, orderId, now);
            } catch (DuplicateKeyException e) {
                log.info("订单已被并发处理, orderId={}", orderId);
                return ApplyResult.alreadyProcessed();
            }

            log.info("更新会员到期时间: userId={}, oldExpire={}, newExpire={}",
                    userId, member.getExpireTime(), newExpire);
            memberMapper.updateExpireTime(userId, newExpire);

            log.info("处理会员权益订单完成, orderId={}", orderId);
            return ApplyResult.success();

        } catch (IllegalArgumentException e) {
            log.warn("参数校验失败, orderId={}: {}", orderId, e.getMessage());
            return ApplyResult.failed("参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("处理订单异常, orderId={}", orderId, e);
            return ApplyResult.failed("处理异常: " + e.getMessage());
        }
    }

    public UserMemberDTO getMemberInfo(Long userId) {
        UserMember entity = memberMapper.selectByUserId(userId);
        return toDTO(entity);
    }

    private UserMemberDTO toDTO(UserMember entity) {
        if (entity == null) {
            return null;
        }
        UserMemberDTO dto = new UserMemberDTO();
        dto.setUserId(entity.getUserId());
        dto.setExpireTime(entity.getExpireTime());
        return dto;
    }

    private LocalDateTime calculateNewExpireTimeInternal(UserMember member, int durationDays, LocalDateTime now) {

        LocalDateTime oldExpire = member.getExpireTime();
        MemberAccumulationStrategy strategyType = properties.getStrategy();

        log.debug("计算到期时间: strategy={}, oldExpire={}, now={}, durationDays={}",
                strategyType, oldExpire, now, durationDays);

        AccumulationStrategy strategy = strategyMap.get(strategyType);
        if (strategy == null) {
            log.warn("未找到策略实现: {}, 使用默认 RESET 策略", strategyType);
            strategy = strategyMap.get(MemberAccumulationStrategy.RESET);
        }

        LocalDateTime baseTime = strategy.calculateBaseTime(oldExpire, now, properties.getGraceDays());
        LocalDateTime newExpire = baseTime.plusDays(durationDays);

        log.debug("计算结果: baseTime={}, newExpire={}", baseTime, newExpire);

        return newExpire;
    }
}
