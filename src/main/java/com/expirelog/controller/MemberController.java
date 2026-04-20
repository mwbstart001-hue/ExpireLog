package com.expirelog.controller;

import com.expirelog.dto.BatchApplyRequest;
import com.expirelog.dto.BatchApplyResult;
import com.expirelog.dto.MemberExpireLogDTO;
import com.expirelog.dto.PageResult;
import com.expirelog.dto.UserMemberDTO;
import com.expirelog.service.MemberExpireLogService;
import com.expirelog.service.MemberExpireService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/apply/batch")
    public BatchApplyResult applyMemberExpireBatch(@RequestBody BatchApplyRequest request) {
        return memberExpireService.applyMemberExpireBatch(request.getOrderIds());
    }

    @PostMapping("/apply/{orderId}/async")
    public ResponseEntity<Void> applyMemberExpireAsync(@PathVariable Long orderId) {
        memberExpireService.applyMemberExpireAsync(orderId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
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
