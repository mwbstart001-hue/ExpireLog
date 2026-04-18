package com.expirelog.controller;

import com.expirelog.entity.UserMember;
import com.expirelog.mapper.UserMemberMapper;
import com.expirelog.service.MemberExpireService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberExpireService memberExpireService;
    private final UserMemberMapper userMemberMapper;

    public MemberController(MemberExpireService memberExpireService,
                            UserMemberMapper userMemberMapper) {
        this.memberExpireService = memberExpireService;
        this.userMemberMapper = userMemberMapper;
    }

    @PostMapping("/apply/{orderId}")
    public String applyMemberExpire(@PathVariable Long orderId) {
        memberExpireService.applyMemberExpire(orderId);
        return "success";
    }

    @GetMapping("/{userId}")
    public UserMember getMemberInfo(@PathVariable Long userId) {
        return userMemberMapper.selectByUserId(userId);
    }
}
