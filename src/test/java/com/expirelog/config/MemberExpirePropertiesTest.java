package com.expirelog.config;

import com.expirelog.enums.MemberAccumulationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("配置类和枚举测试")
class MemberExpirePropertiesTest {

    @Test
    @DisplayName("枚举转换：null 值返回默认 RESET")
    void testEnumFromNull() {
        MemberAccumulationStrategy result = MemberAccumulationStrategy.fromString(null);
        assertEquals(MemberAccumulationStrategy.RESET, result);
    }

    @Test
    @DisplayName("枚举转换：无效值返回默认 RESET")
    void testEnumFromInvalid() {
        MemberAccumulationStrategy result = MemberAccumulationStrategy.fromString("INVALID");
        assertEquals(MemberAccumulationStrategy.RESET, result);
    }

    @Test
    @DisplayName("枚举转换：有效字符串不区分大小写")
    void testEnumFromValid() {
        assertEquals(MemberAccumulationStrategy.STRICT, MemberAccumulationStrategy.fromString("strict"));
        assertEquals(MemberAccumulationStrategy.GRACE, MemberAccumulationStrategy.fromString("GRACE"));
        assertEquals(MemberAccumulationStrategy.RESET, MemberAccumulationStrategy.fromString("Reset"));
    }

    @Test
    @DisplayName("配置类：graceDays 不能为负数")
    void testGraceDays_NonNegative() {
        MemberExpireProperties properties = new MemberExpireProperties();

        properties.setGraceDays(-1);
        assertEquals(0, properties.getGraceDays());

        properties.setGraceDays(7);
        assertEquals(7, properties.getGraceDays());
    }

    @Test
    @DisplayName("配置类：默认策略为 RESET")
    void testDefaultStrategy() {
        MemberExpireProperties properties = new MemberExpireProperties();
        assertEquals(MemberAccumulationStrategy.RESET, properties.getStrategy());
    }

    @Test
    @DisplayName("配置类：可以设置和获取策略")
    void testSetStrategy() {
        MemberExpireProperties properties = new MemberExpireProperties();

        properties.setStrategy(MemberAccumulationStrategy.STRICT);
        assertEquals(MemberAccumulationStrategy.STRICT, properties.getStrategy());

        properties.setStrategy(MemberAccumulationStrategy.GRACE);
        assertEquals(MemberAccumulationStrategy.GRACE, properties.getStrategy());

        properties.setStrategy(MemberAccumulationStrategy.RESET);
        assertEquals(MemberAccumulationStrategy.RESET, properties.getStrategy());
    }
}
