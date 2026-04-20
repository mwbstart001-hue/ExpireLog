package com.expirelog.mapper;

import com.expirelog.entity.MemberExpireLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemberExpireLogMapper {

    boolean existsByOrderId(@Param("orderId") Long orderId);

    void insert(@Param("userId") Long userId,
                @Param("changeDays") Integer changeDays,
                @Param("orderId") Long orderId,
                @Param("createdAt") LocalDateTime createdAt);

    void deleteByOrderId(@Param("orderId") Long orderId);

    void deleteByUserId(@Param("userId") Long userId);

    MemberExpireLog selectByOrderId(@Param("orderId") Long orderId);

    List<MemberExpireLog> selectByUserId(@Param("userId") Long userId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);
}
