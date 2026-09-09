package com.scott.payment.settlement.dto;

import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCommandAudit
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 承载 service-admin 可信注入的人工批次命令审计；请求键用于数据库最终幂等，原因和操作人快照随资金结果持久化。
 * @status : create
 * @param requestKey 人工命令数据库幂等键，最大 64 字符
 * @param reason 人工操作原因，最大 400 字符
 * @param operator service-admin 注入的可信操作主体及客户端快照
 */
public record SettlementCommandAudit(
        String requestKey,
        String reason,
        SettlementOperatorSnapshot operator) {

    public SettlementCommandAudit {
        if (requestKey == null || requestKey.isBlank() || requestKey.trim().length() > 64
                || reason == null || reason.isBlank() || reason.trim().length() > 400) {
            throw new IllegalArgumentException("settlement command audit is invalid");
        }
        requestKey = requestKey.trim();
        reason = reason.trim();
        Objects.requireNonNull(operator, "settlement command operator is required");
    }
}
