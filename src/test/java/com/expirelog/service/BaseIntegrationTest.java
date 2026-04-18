package com.expirelog.service;

import com.expirelog.mapper.MemberExpireLogMapper;
import com.expirelog.mapper.MemberOrderMapper;
import com.expirelog.mapper.UserMemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;

public abstract class BaseIntegrationTest {

    @Autowired
    protected MemberExpireService memberExpireService;

    @Autowired
    protected MemberOrderMapper orderMapper;

    @Autowired
    protected UserMemberMapper memberMapper;

    @Autowired
    protected MemberExpireLogMapper expireLogMapper;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected TransactionTemplate transactionTemplate;

    private static final AtomicLong ID_COUNTER = new AtomicLong(System.currentTimeMillis());

    @PostConstruct
    public void init() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    protected long nextUserId() {
        return ID_COUNTER.incrementAndGet();
    }

    protected long nextOrderId() {
        return ID_COUNTER.incrementAndGet() + 1000000;
    }

    protected void cleanupTestData(long... userIds) {
        transactionTemplate.execute(status -> {
            for (long userId : userIds) {
                memberMapper.deleteByUserId(userId);
                expireLogMapper.deleteByUserId(userId);
            }
            return null;
        });
    }

    protected void cleanupOrderData(long... orderIds) {
        transactionTemplate.execute(status -> {
            for (long orderId : orderIds) {
                expireLogMapper.deleteByOrderId(orderId);
            }
            return null;
        });
    }
}
