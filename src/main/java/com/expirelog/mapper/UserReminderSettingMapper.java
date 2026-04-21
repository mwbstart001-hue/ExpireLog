package com.expirelog.mapper;

import com.expirelog.entity.UserReminderSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserReminderSettingMapper {

    void insert(@Param("userId") Long userId,
                @Param("enabled") boolean enabled,
                @Param("channels") String channels,
                @Param("daysBeforeExpire") Integer daysBeforeExpire);

    void update(@Param("userId") Long userId,
                @Param("enabled") boolean enabled,
                @Param("channels") String channels,
                @Param("daysBeforeExpire") Integer daysBeforeExpire);

    UserReminderSetting selectByUserId(@Param("userId") Long userId);

    void upsert(@Param("userId") Long userId,
                @Param("enabled") boolean enabled,
                @Param("channels") String channels,
                @Param("daysBeforeExpire") Integer daysBeforeExpire);
}
