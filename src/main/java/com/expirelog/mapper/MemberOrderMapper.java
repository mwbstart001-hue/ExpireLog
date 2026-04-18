package com.expirelog.mapper;

import com.expirelog.entity.MemberOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MemberOrderMapper {

    MemberOrder selectPaid(@Param("orderId") Long orderId);

    void insert(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("durationDays") Integer durationDays,
                @Param("status") String status,
                @Param("createdAt") LocalDateTime createdAt);
}
