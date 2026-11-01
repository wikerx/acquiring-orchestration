package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道回调内部受理命令，位于 service-payment 内部接口 DTO 层，仅承载完成安全校验后的标准事件和不可逆报文摘要。
 */
@Data
public class TransactionChannelCallbackCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道编码，如 MPGS。
     */
    @NotBlank(message = "channelCode is required")
    private String channelCode;

    /**
     * 回调类型，默认使用 CHANNEL_CALLBACK。
     */
    private String callbackType;

    /**
     * 平台交易 ID；MPGS 回调优先从 order.id 解析。
     */
    private String transactionId;

    /**
     * 渠道订单号；MPGS 对应 order.id。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID；MPGS 对应 transaction.id。
     */
    private String channelTransactionId;

    /**
     * 渠道事件类型。
     */
    private String channelEventType;

    /**
     * 验签所使用的渠道凭据标识，例如渠道 MID；不得保存密钥。
     */
    private String channelCredentialHint;

    /**
     * 渠道稳定事件号，是 Inbox 数据库幂等键的必要组成部分。
     */
    private String callbackEventId;

    /**
     * 渠道协议归一化后的回调业务类型。
     */
    private String callbackKind;

    /**
     * 原始渠道状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String rawChannelStatus;

    /**
     * {@code channelTradeStatus}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String channelTradeStatus;

    /**
     * 金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal amount;

    /**
     * 币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String currency;

    /**
     * 渠道响应编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String channelResponseCode;

    /**
     * 渠道响应说明字段，保存 {@code TransactionChannelCallbackCommandDTO} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String channelResponseMessage;

    /**
     * 渠道协议解析出的非敏感扩展字段。
     */
    private Map<String, String> extension = new LinkedHashMap<>();

    /**
     * 回调请求 URI。
     */
    private String requestUri;

    /**
     * HTTP 方法。
     */
    private String httpMethod;

    /**
     * 回调来源 IP。
     */
    private String sourceIp;

    /**
     * 请求头摘要，进入数据库前由支付服务统一转 JSON 和脱敏。
     */
    private Map<String, String> requestHeaders;

    /**
     * 回调原始报文的精确字节数；支付核心不接收或保存可逆原文。
     */
    private Integer requestBodyLength;

    /**
     * 回调原始报文的 SHA-256 十六进制摘要，用于审计关联和完整性排查。
     */
    private String requestBodySha256;

    /**
     * 签名校验是否通过。
     */
    private Boolean signatureValid;

    /**
     * IP 白名单是否通过。
     */
    private Boolean ipAllowed;

    /**
     * OpenAPI 入口收到回调的时间。
     */
    private LocalDateTime receivedTime;
}
