package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MPGS 3DS Direct API 认证请求。
 */
@Data
public class MpgsThreeDsAuthenticationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标渠道编码，调用 MPGS 时必须为平台登记的 MPGS 编码。
     */
    private String channelCode;

    /**
     * 平台支付操作单号，用于关联本次 3DS 认证动作。
     */
    private String operationId;

    /**
     * 平台交易号，用于认证结果回写和审计关联。
     */
    private String transactionId;

    /**
     * MPGS 订单号；首次认证尚未建单时允许为空。
     */
    private String channelOrderNo;

    /**
     * MPGS authentication transaction id；继续认证时必填。
     */
    private String authenticationTransactionId;

    /**
     * 平台商户号，属于可识别业务数据，不得写入无访问控制的日志。
     */
    private String merchantId;

    /**
     * 商户订单号，用于商户侧幂等与查询关联。
     */
    private String merchantOrderNo;

    /**
     * 发送给 MPGS 的 merchant order id。
     */
    private String merchantOrderId;

    /**
     * 平台支付方式编码，例如 CARD。
     */
    private String paymentMethod;

    /**
     * 认证对应交易金额，单位为币种主单位；使用十进制定点值，禁止浮点换算。
     */
    private BigDecimal amount;

    /**
     * ISO 4217 三位币种代码，决定金额精度。
     */
    private String currency;

    /**
     * 平台交易时间，用于分表路由和认证审计，精度为毫秒。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 完整卡号 PAN，属于高敏感数据；只允许在渠道调用内短暂使用，禁止日志和持久化。
     */
    private String cardNo;

    /**
     * 卡片有效期月份，格式 MM；属于持卡人敏感数据。
     */
    private String expirationMonth;

    /**
     * 卡片有效期年份，格式 YY 或 YYYY，按渠道协议转换。
     */
    private String expirationYear;

    /**
     * CVV/CVC 安全码，属于禁止存储数据；渠道请求完成后不得保留或记录。
     */
    private String securityCode;

    /** 卡面持卡人姓名，仅用于当前 MPGS 认证请求。 */
    private String cardholderName;

    /**
     * 卡品牌编码，用于 MPGS 协议映射。
     */
    private String cardBrand;

    /**
     * 3DS 认证完成后的平台回跳地址，必须来自受控收银台域名。
     */
    private String redirectResponseUrl;

    /** INITIATE AUTHENTICATION 的 order.notificationUrl。 */
    private String notificationUrl;

    /**
     * 3DS 浏览器信息 JSON，可能包含设备与网络指纹，日志只能记录摘要。
     */
    private String browserInfoJson;

    /** PAYER_BROWSER 认证使用的付款人真实 IP，不得落库或记录明文日志。 */
    private String payerIp;

    /**
     * 账单持卡人信息，包含姓名和地址等个人信息，只按渠道最小必要字段传递。
     */
    private ChannelPaymentRequest.BillingInfo billingInfo;

    /**
     * MPGS 扩展字段；仅允许受控协议键，不得放入 PAN、CVV、密钥或 token。
     */
    private Map<String, String> extension = new HashMap<>();
}
