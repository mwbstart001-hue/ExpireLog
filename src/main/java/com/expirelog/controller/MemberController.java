package com.expirelog.controller;

import com.expirelog.dto.MemberExpireLogDTO;
import com.expirelog.dto.PageResult;
import com.expirelog.dto.UserMemberDTO;
import com.expirelog.service.MemberExpireLogService;
import com.expirelog.service.MemberExpireService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberExpireService memberExpireService;
    private final MemberExpireLogService memberExpireLogService;

    public MemberController(MemberExpireService memberExpireService,
                            MemberExpireLogService memberExpireLogService) {
        this.memberExpireService = memberExpireService;
        this.memberExpireLogService = memberExpireLogService;
    }

    @PostMapping("/apply/{orderId}")
    public String applyMemberExpire(@PathVariable Long orderId) {
        memberExpireService.applyMemberExpire(orderId);
        return "success";
    }

    @GetMapping("/{userId}")
    public UserMemberDTO getMemberInfo(@PathVariable Long userId) {
        return memberExpireService.getMemberInfo(userId);
    }

    @GetMapping("/{userId}/logs")
    public PageResult<MemberExpireLogDTO> getMemberLogs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return memberExpireLogService.getByUserId(userId, page, size);
    }

    @GetMapping("/logs/order/{orderId}")
    public MemberExpireLogDTO getLogByOrderId(@PathVariable Long orderId) {
        return memberExpireLogService.getByOrderId(orderId);
    }
}
