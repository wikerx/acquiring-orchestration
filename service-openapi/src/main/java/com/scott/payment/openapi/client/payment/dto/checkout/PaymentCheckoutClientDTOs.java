package com.scott.payment.openapi.client.payment.dto.checkout;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * service-openapi 调用 service-payment Hosted Checkout 内部接口 DTO。
 */
public final class PaymentCheckoutClientDTOs {

    private PaymentCheckoutClientDTOs() {
    }

    /**
     * service-openapi 创建 Hosted Checkout 会话的内部请求。
     */
    @Data
    public static class SessionCreateRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 已认证的平台商户号。 */
        private String merchantId;

        /** 商户订单号。 */
        private String merchantOrderNo;

        /** 商户本次创建请求号，用于内部幂等关联。 */
        private String merchantRequestId;

        /** 创建请求规范化后的摘要，用于拒绝幂等键复用不同载荷。 */
        private String requestFingerprint;

        /** 订单金额，单位为 {@link #currency} 主币种单位。 */
        private BigDecimal amount;

        /** ISO 4217 三位币种代码。 */
        private String currency;

        /** 币种小数位数，由 ISO 字典解析，不用于金额舍入。 */
        private Integer currencyExponent;

        /** 支付动作，例如直接支付或预授权。 */
        private String paymentAction;

        /** 付款页展示的订单主题。 */
        private String orderSubject;

        /** 付款页展示的订单说明。 */
        private String orderDescription;

        /** 商品明细 JSON 快照，不包含卡号、CVV 或密钥。 */
        private String orderItemsJson;

        /** 商户允许的支付方式和 3DS 模式。 */
        private List<AllowedPaymentMethod> allowedPaymentMethods;

        /** 收银台前端域名；已在 OpenAPI 层完成协议和长度校验。 */
        private String checkoutDomain;

        /** 收银台语言区域标识。 */
        private String locale;

        /** 付款页展示的商户名称。 */
        private String merchantDisplayName;

        /** 付款页展示的商户 Logo 地址。 */
        private String merchantLogoUrl;

        /** 支付完成后的商户返回地址。 */
        private String merchantReturnUrl;

        /** 付款取消后的商户返回地址。 */
        private String merchantCancelUrl;

        /** 商户通知 URL 摘要；内部请求不传递原始通知地址。 */
        private String merchantNotifyUrlHash;

        /** ISO 3166-1 alpha-3 付款人国家/地区代码。 */
        private String payerCountry;

        /** 付款人邮箱脱敏展示值。 */
        private String payerEmailMasked;

        /** 付款人邮箱不可逆摘要，用于风控匹配。 */
        private String payerEmailHash;

        /** 是否允许失败后重试，1 表示允许、0 表示禁止。 */
        private Integer retryAllowed;

        /** 会话允许的最大支付尝试次数。 */
        private Integer maxAttemptCount;

        /** 会话过期时间，按内部服务约定时区解释。 */
        private LocalDateTime expireTime;

        /** 创建请求来源，例如商户 OpenAPI。 */
        private String requestSource;

        /** 跨 OpenAPI、payment 和渠道链路的追踪号。 */
        private String traceId;
    }

    /**
     * 内部会话请求中的单个允许支付方式。
     */
    @Data
    public static class AllowedPaymentMethod implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 平台支付方式编码。 */
        private String paymentMethod;

        /** 可选指定渠道编码；为空时由支付服务路由。 */
        private String channelCode;

        /** 允许的卡品牌或支付品牌。 */
        private List<String> brands;

        /** 3DS 执行模式。 */
        private String threeDsMode;
    }

    /**
     * service-payment 创建 Hosted Checkout 会话的内部响应。
     */
    @Data
    public static class SessionCreateResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Hosted Checkout 会话号。 */
        private String checkoutSessionId;

        /** 会话访问令牌的内部标识，不是浏览器可直接使用的原始 token。 */
        private String checkoutTokenId;

        /** 付款人访问收银台的 URL，可能携带一次性 token，禁止写日志。 */
        private String checkoutUrl;

        /** 会话当前状态。 */
        private String checkoutStatus;

        /** 会话过期时间。 */
        private LocalDateTime expireTime;

        /** service-payment 是否命中既有幂等会话。 */
        private Boolean idempotentHit;
    }

    /**
     * service-openapi 查询付款页会话的内部请求。
     */
    @Getter
    @Setter
    public static class SessionQueryRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 浏览器 opaque token 的 HMAC 摘要，内部服务不接收原始 token。 */
        private String tokenHash;

        /** 可选页面封面或主题标识，不作为安全凭据。 */
        private String cover;

        /** 客户端 IP 摘要，不传递原始地址。 */
        private String clientIpHash;

        /** User-Agent 摘要，不传递完整设备字符串。 */
        private String userAgentHash;

        /** Origin 摘要，用于来源一致性审计。 */
        private String originHash;

        /** Referer 摘要，用于来源一致性审计。 */
        private String refererHash;

        /** 前端设备标识摘要，不能作为唯一认证凭据。 */
        private String deviceIdHash;

        /** 浏览器语言。 */
        private String language;

        /** 浏览器时区偏移。 */
        private String timezoneOffset;

        /** 跨服务追踪号。 */
        private String traceId;
    }

    /**
     * service-payment 返回的付款页会话展示数据。
     */
    @Data
    public static class SessionQueryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Hosted Checkout 会话号。 */
        private String checkoutSessionId;

        /** 付款页状态。 */
        private String pageState;

        /** 可公开展示的商户资料。 */
        private Merchant merchant;

        /** 订单金额与商品快照。 */
        private Order order;

        /** 当前会话允许的支付方式。 */
        private List<PaymentMethod> paymentMethods;

        /** 有效期、重试次数和轮询配置。 */
        private Checkout checkout;
    }

    /**
     * service-openapi 提交付款尝试的内部请求。
     */
    @Getter
    @Setter
    public static class PaymentSubmitRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 浏览器 opaque token 的 HMAC 摘要，内部服务不接收原始 token。 */
        private String tokenHash;

        /** Hosted Checkout 会话号。 */
        private String checkoutSessionId;

        /** 单次支付尝试请求号，用于提交幂等。 */
        private String attemptRequestId;

        /** 付款人选择的支付方式编码。 */
        private String paymentMethod;

        /** 提交请求摘要，用于检测同一幂等号对应不同载荷。 */
        private String requestFingerprint;

        /** 跨服务追踪号。 */
        private String traceId;

        /** 客户端 IP 摘要。 */
        private String clientIpHash;

        /** User-Agent 摘要。 */
        private String userAgentHash;

        /** Origin 摘要。 */
        private String originHash;

        /** Referer 摘要。 */
        private String refererHash;

        /** 3DS 所需浏览器信息 JSON；不得包含完整 PAN 或 CVV。 */
        private String browserInfoJson;

        /** 设备信息脱敏 JSON；原始设备采集报文不得持久化。 */
        private String deviceInfoJson;

        /** 敏感卡数据，仅允许短暂传入支付处理，禁止日志或普通业务持久化。 */
        private CardInfo cardInfo;

        /** 账单持卡人资料，用于渠道和 3DS 校验，日志必须脱敏。 */
        private BillingCardHolderInfo billingCardHolderInfo;
    }

    /**
     * service-openapi 查询付款尝试状态的内部请求。
     */
    @Getter
    @Setter
    public static class PaymentStatusRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 付款人 URL token 的 HMAC 摘要，内部服务不接收原始 token。
         */
        private String tokenHash;

        /**
         * 收银台会话号，用于和 token 摘要共同校验访问边界。
         */
        private String checkoutSessionId;

        /**
         * 可选付款尝试号；为空时 service-payment 返回当前会话最新尝试。
         */
        private String checkoutAttemptId;

        /**
         * TraceId 贯穿 OpenAPI、payment 和渠道调用日志。
         */
        private String traceId;

        /**
         * 浏览器 IP 摘要，供非法访问和轮询异常审计使用。
         */
        private String clientIpHash;

        /**
         * 浏览器 UA 摘要，避免日志和数据库保存完整设备指纹。
         */
        private String userAgentHash;
    }

    /**
     * 3DS bridge 回跳内部请求，原始 return token 和认证数据在 OpenAPI 层已摘要/脱敏。
     */
    @Getter
    @Setter
    public static class ThreeDsReturnRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 3DS return token 的 HMAC 摘要，用于绑定具体付款尝试。
         */
        private String threeDsReturnTokenHash;

        /**
         * 收银台会话号，用于校验回跳不能串到其他订单。
         */
        private String checkoutSessionId;

        /**
         * 收银台付款尝试号，用于定位等待 3DS 的尝试记录。
         */
        private String checkoutAttemptId;

        /**
         * 付款人浏览器回传的 3DS 数据脱敏文本，仅用于排查和渠道状态对账。
         */
        private String authenticationDataJsonMasked;

        /**
         * TraceId 贯穿 3DS 回跳链路。
         */
        private String traceId;

        /**
         * 回跳浏览器 IP 摘要，用于非法回跳审计。
         */
        private String clientIpHash;

        /**
         * 回跳浏览器 UA 摘要，用于非法回跳审计。
         */
        private String userAgentHash;
    }

    /**
     * service-payment 返回给 hosted-checkout 的统一付款结果模型。
     */
    @Data
    public static class PaymentResultResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 收银台会话号。
         */
        private String checkoutSessionId;

        /**
         * 当前付款尝试号。
         */
        private String checkoutAttemptId;

        /**
         * 前端页面状态，例如 PAYING、SUCCESS、FAILED、PROCESSING 或 CHALLENGE_REQUIRED。
         */
        private String pageState;

        /**
         * 终态或处理中页面可展示的付款摘要。
         */
        private PaymentResult result;

        /**
         * 3DS 下一步动作，存在时前端应优先渲染认证 bridge。
         */
        private ThreeDsAction threeDsAction;

        /**
         * 失败详情，包含可重试标记和剩余次数。
         */
        private Failure failure;

        /**
         * 处理中页面轮询配置。
         */
        private Polling polling;

        /**
         * 付款完成后的商户 return/cancel 跳转地址。
         */
        private Action actions;
    }

    /**
     * 收银台展示用商户信息，非法访问场景可能为空。
     */
    @Data
    public static class Merchant implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户主体或子商户展示名称。
         */
        private String displayName;

        /**
         * 商户 Logo 地址，当前为空时由前端展示平台默认标识。
         */
        private String logoUrl;
    }

    /**
     * 收银台展示用订单快照，来自商户创建会话时的请求。
     */
    @Data
    public static class Order implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户订单号。
         */
        private String orderNo;

        /**
         * 订单标题。
         */
        private String subject;

        /**
         * 订单描述。
         */
        private String description;

        /**
         * 订单展示金额。
         */
        private BigDecimal amount;

        /**
         * ISO 4217 币种代码。
         */
        private String currency;

        /**
         * 币种小数位，用于前端金额格式化。
         */
        private Integer currencyExponent;

        /**
         * 商品明细 JSON 快照，来源于商户创建会话请求。
         */
        private String itemsJson;
    }

    /**
     * 商户允许的收银台付款方式快照。
     */
    @Data
    public static class PaymentMethod implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台付款方式，例如 BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 优先使用的渠道编码。
         */
        private String channelCode;

        /**
         * 允许展示的卡组织或钱包品牌。
         */
        private List<String> brands;

        /**
         * 3DS 模式，例如 REQUIRED、OPTIONAL 或通道默认值。
         */
        private String threeDsMode;
    }

    /**
     * 收银台会话展示和轮询配置。
     */
    @Data
    public static class Checkout implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 会话过期时间，OpenAPI 层负责转换为统一响应时区格式。
         */
        private LocalDateTime expireTime;

        /**
         * 是否允许失败后重新付款。
         */
        private Boolean retryAllowed;

        /**
         * 当前剩余可支付尝试次数。
         */
        private Integer remainingAttemptCount;

        /**
         * 前端处理中页面建议轮询间隔。
         */
        private Integer pollingIntervalSeconds;
    }

    /**
     * 付款人卡信息，只在 OpenAPI 到 service-payment 的内存调用链中过境。
     */
    @Getter
    @Setter
    public static class CardInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 明文卡号，禁止日志输出和持久化保存。
         */
        private String cardNo;

        /**
         * 卡有效期月份。
         */
        private String expirationMonth;

        /**
         * 卡有效期年份。
         */
        private String expirationYear;

        /**
         * CVV/CVC，仅用于本次渠道请求，禁止保存。
         */
        private String securityCode;

        /**
         * 持卡人姓名，用于 3DS 和渠道授权请求。
         */
        private String cardholderName;
    }

    /**
     * 付款人账单信息，用于 MPGS 3DS 和后续授权请求。
     */
    @Getter
    @Setter
    public static class BillingCardHolderInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 付款人名字。
         */
        private String firstName;

        /**
         * 付款人姓氏。
         */
        private String lastName;

        /**
         * 付款人邮箱，调用方不得输出明文日志。
         */
        private String email;

        /**
         * 付款人电话。
         */
        private String phone;

        /**
         * 账单国家/地区。
         */
        private String country;

        /**
         * 账单州/省。
         */
        private String state;

        /**
         * 账单城市。
         */
        private String city;

        /**
         * 账单街道。
         */
        private String street;

        /**
         * 账单邮编。
         */
        private String postal;
    }

    /**
     * 结果页付款摘要，卡号只允许展示掩码。
     */
    @Data
    public static class PaymentResult implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 付款金额。
         */
        private BigDecimal amount;

        /**
         * 付款币种。
         */
        private String currency;

        /**
         * 商户订单号。
         */
        private String merchantOrderNo;

        /**
         * 实际使用的付款方式。
         */
        private String paymentMethod;

        /**
         * 卡组织品牌，例如 VISA、MASTERCARD。
         */
        private String cardBrand;

        /**
         * 掩码卡号，前端结果页只展示该字段。
         */
        private String cardNumberMasked;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 渠道授权码。
         */
        private String authCode;
    }

    /**
     * 3DS 页面动作，承载 ACS HTML 或后续 bridge 地址。
     */
    @Data
    public static class ThreeDsAction implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 前端动作类型，例如 HTML_CHALLENGE。
         */
        private String actionType;

        /**
         * 渠道返回的 3DS HTML，进入前端前应保持原样但不得写入明文日志。
         */
        private String html;

        /**
         * 3DS 完成后的平台回跳地址。
         */
        private String returnUrl;

        /**
         * 3DS 质询超时时间。
         */
        private Integer timeoutSeconds;
    }

    /**
     * 收银台失败结果，负责告诉前端能否继续重试。
     */
    @Data
    public static class Failure implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台失败原因码。
         */
        private String reasonCode;

        /**
         * 可展示给付款人的失败提示。
         */
        private String message;

        /**
         * 当前失败后是否允许重试。
         */
        private Boolean retryAllowed;

        /**
         * 失败后剩余可尝试次数。
         */
        private Integer remainingAttemptCount;
    }

    /**
     * 前端处理中页面轮询参数。
     */
    @Data
    public static class Polling implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 状态查询地址，由 hosted-checkout 前端路由消费。
         */
        private String statusUrl;

        /**
         * 建议轮询间隔秒数。
         */
        private Integer intervalSeconds;

        /**
         * 最大轮询间隔秒数，前端退避策略不应超过该值。
         */
        private Integer maxIntervalSeconds;
    }

    /**
     * 收银台完成后的商户跳转动作。
     */
    @Data
    public static class Action implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 支付终态后跳转商户页面的 returnUrl，不是后端回调地址。
         */
        private String returnUrl;

        /**
         * 付款人取消时跳转商户页面的 cancelUrl。
         */
        private String cancelUrl;
    }
}
