package com.expirelog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MemberExpireLogMapper {

    boolean existsByOrderId(@Param("orderId") Long orderId);

    void insert(@Param("userId") Long userId,
                @Param("changeDays") Integer changeDays,
                @Param("orderId") Long orderId,
                @Param("createdAt") LocalDateTime createdAt);

    void deleteByOrderId(@Param("orderId") Long orderId);

    void deleteByUserId(@Param("userId") Long userId);
}
