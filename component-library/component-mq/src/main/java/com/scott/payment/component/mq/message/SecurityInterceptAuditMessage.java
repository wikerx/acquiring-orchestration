package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptAuditMessage
 * @date : 2026-08-01 18:00
 * @email : scott_x@163.com
 * @description : OpenAPI 安全拦截审计公共 MQ 契约，仅传输已截断和脱敏的排查元数据
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityInterceptAuditMessage extends BaseMqMessage {

    private static final long serialVersionUID = 1L;

    /** 安全事件号，同时是数据库最终幂等键。 */
    private String eventNo;

    /** 事件发生时间，精度为毫秒。 */
    private LocalDateTime eventTime;

    /** 来源层级，例如 OPENAPI 或 CHANNEL。 */
    private String sourceLayer;

    /** 安全事件类型，例如 OPENAPI_IP_DENIED。 */
    private String eventType;

    /** 风险等级：LOW、MEDIUM、HIGH 或 CRITICAL。 */
    private String riskLevel;

    /** 处置动作，安全拦截事件固定为 BLOCK。 */
    private String action;

    /** 商户号；无法完成商户身份解析时允许为空。 */
    private String merchantId;

    /** 客户端 IP，优先使用网关写入的可信客户端 IP。 */
    private String clientIp;

    /** HTTP 请求方法，最长 16 字符。 */
    private String requestMethod;

    /** HTTP 请求路径，不含查询参数。 */
    private String requestPath;

    /** 请求级业务标识；上游未传入时允许为空。 */
    private String requestId;

    /** 已截断的 User-Agent，不含认证凭据。 */
    private String userAgent;

    /** 拦截原因码。 */
    private String reasonCode;

    /** 脱敏且截断后的拦截原因说明。 */
    private String reasonMessage;

    /** 采集事件的服务名。 */
    private String serviceName;

    /** 命中的安全规则编码。 */
    private String hitRuleCode;

    /** 脱敏请求头摘要，禁止包含 Authorization、Cookie、密钥或完整密文。 */
    private String headerSummary;

    /**
     * 获取数据库最终幂等键。
     *
     * @return 安全事件号
     */
    public String idempotentKey() {
        return eventNo;
    }
}
