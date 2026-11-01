package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 收银台 BIN 品牌查询命令，只允许 6 到 11 位前缀。
 */
@Data
public class PaymentCheckoutCardBinCommandDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * {@code tokenHash}，用于以不可逆摘要关联敏感原文或大报文。
     */
    @NotBlank(message = "tokenHash is required")
    private String tokenHash;
    /**
     * 当前收银台会话号，必须与令牌摘要解析出的会话一致。
     */
    @NotBlank(message = "checkoutSessionId is required")
    private String checkoutSessionId;
    /**
     * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
     */
    @NotBlank(message = "cardBin is required")
    @Pattern(regexp = "^\\d{6,11}$", message = "cardBin format does not match")
    private String cardBin;
    /**
     * {@code traceId}，用于定位 {@code PaymentCheckoutCardBinCommandDTO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与日志 MDC 和 X-Trace-Id 请求头共同串联一次链路。
     * </p>
     */
    private String traceId;
}
