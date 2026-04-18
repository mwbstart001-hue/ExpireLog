package com.expirelog.controller;

import com.expirelog.entity.UserMember;
import com.expirelog.service.MemberExpireService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberExpireService memberExpireService;

    public MemberController(MemberExpireService memberExpireService) {
        this.memberExpireService = memberExpireService;
    }

    @PostMapping("/apply/{orderId}")
    public String applyMemberExpire(@PathVariable Long orderId) {
        memberExpireService.applyMemberExpire(orderId);
        return "success";
    }

    @GetMapping("/{userId}")
    public UserMember getMemberInfo(@PathVariable Long userId) {
        return memberExpireService.getMemberInfo(userId);
    }
}
