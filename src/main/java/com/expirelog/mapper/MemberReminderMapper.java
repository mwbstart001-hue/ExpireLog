package com.expirelog.mapper;

import com.expirelog.entity.MemberReminder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemberReminderMapper {

    void insert(@Param("userId") Long userId,
                @Param("remindTime") LocalDateTime remindTime,
                @Param("channel") String channel,
                @Param("status") String status);

    void updateStatus(@Param("id") Long id,
                      @Param("status") String status);

    MemberReminder selectById(@Param("id") Long id);

    List<MemberReminder> selectByUserId(@Param("userId") Long userId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);

    List<MemberReminder> selectPendingByChannel(@Param("channel") String channel,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    int countPendingByChannel(@Param("channel") String channel);

    boolean existsByUserAndTimeAndChannel(@Param("userId") Long userId,
                                            @Param("remindTime") LocalDateTime remindTime,
                                            @Param("channel") String channel);

    void deleteByUserId(@Param("userId") Long userId);
}
