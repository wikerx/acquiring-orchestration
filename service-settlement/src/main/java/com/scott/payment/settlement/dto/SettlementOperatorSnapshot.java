package com.scott.payment.settlement.dto;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementOperatorSnapshot
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 冻结 service-admin 解析的登录主体、角色、客户端环境和操作时间；零号主体仅保留给受约束的 service-settlement 系统任务。
 * @status : create
 * @param accountId 可信管理账户 ID；0 仅允许受约束系统主体
 * @param accountName 管理账户展示名，不包含登录凭据
 * @param roleSnapshot 操作时角色权限快照
 * @param clientIp 客户端 IP 审计值
 * @param userAgent 客户端 User-Agent 审计值
 * @param operationTime 实际操作时间，精度为毫秒
 */
public record SettlementOperatorSnapshot(
        Long accountId,
        String accountName,
        String roleSnapshot,
        String clientIp,
        String userAgent,
        LocalDateTime operationTime) {

    public SettlementOperatorSnapshot {
        boolean systemOperator = Long.valueOf(0L).equals(accountId)
                && "service-settlement".equals(accountName)
                && "SYSTEM".equals(roleSnapshot);
        if (accountId == null || accountId < 0 || (accountId == 0 && !systemOperator)
                || invalid(accountName, 128) || invalid(roleSnapshot, 1000)
                || invalid(clientIp, 64) || invalid(userAgent, 500)
                || operationTime == null) {
            throw new IllegalArgumentException("settlement operator snapshot is invalid");
        }
        accountName = accountName.trim();
        roleSnapshot = roleSnapshot.trim();
        clientIp = clientIp.trim();
        userAgent = userAgent.trim();
    }

    private static boolean invalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.trim().length() > maxLength;
    }
}
