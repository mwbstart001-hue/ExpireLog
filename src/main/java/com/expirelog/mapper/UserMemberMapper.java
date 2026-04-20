package com.expirelog.mapper;

import com.expirelog.entity.UserMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserMemberMapper {

    UserMember selectForUpdate(@Param("userId") Long userId);

    UserMember selectByUserId(@Param("userId") Long userId);

    void updateExpireTime(@Param("userId") Long userId, @Param("expireTime") LocalDateTime expireTime);

    void insert(@Param("userId") Long userId, @Param("expireTime") LocalDateTime expireTime);

    void deleteByUserId(@Param("userId") Long userId);

    void ensureExists(@Param("userId") Long userId, @Param("defaultExpireTime") LocalDateTime defaultExpireTime);
}
