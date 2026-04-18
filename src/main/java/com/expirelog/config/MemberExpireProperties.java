package com.expirelog.config;

import com.expirelog.enums.MemberAccumulationStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "member.expire")
public class MemberExpireProperties {

    private MemberAccumulationStrategy strategy = MemberAccumulationStrategy.RESET;

    private Integer graceDays = 0;

    public MemberAccumulationStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = MemberAccumulationStrategy.fromString(strategy);
    }

    public void setStrategy(MemberAccumulationStrategy strategy) {
        this.strategy = strategy;
    }

    public Integer getGraceDays() {
        return graceDays;
    }

    public void setGraceDays(Integer graceDays) {
        this.graceDays = graceDays != null && graceDays >= 0 ? graceDays : 0;
    }
}
