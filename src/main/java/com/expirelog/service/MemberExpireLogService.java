package com.expirelog.service;

import com.expirelog.dto.MemberExpireLogDTO;
import com.expirelog.dto.PageResult;
import com.expirelog.entity.MemberExpireLog;
import com.expirelog.mapper.MemberExpireLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MemberExpireLogService {

    private static final Logger log = LoggerFactory.getLogger(MemberExpireLogService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final MemberExpireLogMapper expireLogMapper;

    public MemberExpireLogService(MemberExpireLogMapper expireLogMapper) {
        this.expireLogMapper = expireLogMapper;
    }

    public MemberExpireLogDTO getByOrderId(Long orderId) {
        log.debug("查询订单流水: orderId={}", orderId);
        if (orderId == null) {
            return null;
        }
        MemberExpireLog entity = expireLogMapper.selectByOrderId(orderId);
        return toDTO(entity);
    }

    public PageResult<MemberExpireLogDTO> getByUserId(Long userId, int page, int size) {
        log.debug("查询用户流水: userId={}, page={}, size={}", userId, page, size);

        if (userId == null) {
            return new PageResult<>(List.of(), 0, page, normalizeSize(size));
        }

        int validPage = Math.max(page, 1);
        int validSize = normalizeSize(size);

        int offset = (validPage - 1) * validSize;
        int total = expireLogMapper.countByUserId(userId);
        List<MemberExpireLog> logs = expireLogMapper.selectByUserId(userId, offset, validSize);

        log.debug("查询用户流水结果: userId={}, total={}, count={}", userId, total, logs.size());

        List<MemberExpireLogDTO> dtos = logs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResult<>(dtos, total, validPage, validSize);
    }

    private MemberExpireLogDTO toDTO(MemberExpireLog entity) {
        if (entity == null) {
            return null;
        }
        MemberExpireLogDTO dto = new MemberExpireLogDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setChangeDays(entity.getChangeDays());
        dto.setOrderId(entity.getOrderId());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            log.warn("请求的 pageSize={} 超过最大值 {}, 使用最大值", size, MAX_PAGE_SIZE);
            return MAX_PAGE_SIZE;
        }
        return size;
    }
}
