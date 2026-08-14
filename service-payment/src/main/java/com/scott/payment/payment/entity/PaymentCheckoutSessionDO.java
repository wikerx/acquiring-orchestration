package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hosted Checkout 会话主表实体。
 */
@Data
@TableName("payment_checkout_session")
public class PaymentCheckoutSessionDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台 Hosted Checkout 会话号。 */
    private String checkoutSessionId;
    /** 会话所属商户号。 */
    private String merchantId;
    /** 商户订单号。 */
    private String merchantOrderNo;
    /** 商户会话创建请求号，与商户号共同用于持久化幂等。 */
    private String merchantRequestId;
    /** 原加密请求指纹，用于识别幂等报文冲突。 */
    private String requestFingerprint;
    /** 会话对应的支付动作。 */
    private String paymentAction;
    /** 支付接入类型，例如 HOSTED_CHECKOUT。 */
    private String integrationType;
    /** 会话业务状态；终态不得被普通更新覆盖。 */
    private String checkoutStatus;
    /** 会话当前内部处理阶段。 */
    private String processStage;
    /** 最近一次业务状态变化时间。 */
    private LocalDateTime lastStatusTime;
    /** 关联的初始支付动作号。 */
    private String operationId;
    /** 会话关联的根交易号。 */
    private String rootTransactionId;
    /** 最近一次支付尝试关联的交易号。 */
    private String latestTransactionId;
    /** 交易业务时间，用于分片交易定位。 */
    private LocalDateTime transactionDateTime;
    /** 会话展示金额的 ISO 4217 币种。 */
    private String labelCurrency;
    /** 会话展示金额，单位为 {@link #labelCurrency} 的主币种单位。 */
    private BigDecimal labelAmount;
    /** 展示币种小数位数。 */
    private Integer currencyExponent;
    /** 创建会话时固化的订单主题。 */
    private String orderSubject;
    /** 创建会话时固化的订单说明。 */
    private String orderDescription;
    /** 商品明细 JSON 快照，不得包含支付敏感数据。 */
    private String orderItemsJson;
    /** 允许支付方式 JSON 快照。 */
    private String allowedPaymentMethodsJson;
    /** 付款人最终选择的支付方式。 */
    private String selectedPaymentMethod;
    /** 付款人最终使用的支付品牌。 */
    private String selectedPaymentBrand;
    /** 当前尝试命中的渠道编码。 */
    private String channelCode;
    /** 当前尝试命中的渠道商户配置主键。 */
    private Long channelMidConfigId;
    /** 付款页公开展示的商户名称。 */
    private String merchantDisplayName;
    /** 付款页公开展示的商户 Logo 地址。 */
    private String merchantLogoUrl;
    /** 商户通知地址明文；只允许用于创建通知任务，禁止完整写入日志。 */
    private String merchantNotifyUrl;
    /** 子商户完整明文 JSON 快照。 */
    private String subMerchantInfoJson;
    /** 付款人预填信息明文 JSON 快照。 */
    private String payerInfoJson;
    /** 持卡人账单预填信息明文 JSON 快照。 */
    private String billingInfoJson;
    /** 收货信息结构化 JSON；生成交易后写入结构化明文快照表。 */
    private String shippingInfoJson;
    /** 交易完成后 Form POST 的商户结果页地址明文。 */
    private String redirectUrl;
    /** 付款页语言或地区标识。 */
    private String locale;
    /** 付款人国家或地区代码。 */
    private String payerCountry;
    /** 付款人邮箱明文。 */
    private String payerEmail;
    /** 付款人邮箱摘要。 */
    private String payerEmailHash;
    /** 是否允许失败后重试：0 否，1 是。 */
    private Integer retryAllowed;
    /** 会话允许的最大支付尝试次数。 */
    private Integer maxAttemptCount;
    /** 已创建的支付尝试数量。 */
    private Integer attemptCount;
    /** 支付成功尝试号；会话未成功时为空。 */
    private String successAttemptId;
    /** 最近一次支付尝试号。 */
    private String lastAttemptId;
    /** 平台收银台前端受控基础地址。 */
    private String checkoutDomain;
    /** 会话失效时间。 */
    private LocalDateTime expireTime;
    /** 会话支付成功时间。 */
    private LocalDateTime paidTime;
    /** 会话取消时间。 */
    private LocalDateTime cancelTime;
    /** 会话被安全策略阻断时间。 */
    private LocalDateTime blockedTime;
    /** 会话阻断原因编码。 */
    private String blockReasonCode;
    /** 付款人最近打开收银台时间。 */
    private LocalDateTime lastOpenTime;
    /** 付款人最近提交支付时间。 */
    private LocalDateTime lastSubmitTime;
    /** 渠道结果待确认时的下次匹配时间。 */
    private LocalDateTime nextChannelMatchTime;
    /** 已脱敏的付款页结果快照，不得包含 PAN、CVV、CAVV 或令牌。 */
    private String resultSnapshot;
    /** 乐观锁版本号。 */
    private Integer version;
    /** 逻辑删除标识。 */
    private Integer deleted;
    /** 数据库记录创建时间。 */
    private LocalDateTime createTime;
    /** 数据库记录最近更新时间。 */
    private LocalDateTime updateTime;
}
