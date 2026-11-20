package com.scott.payment.link.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 固定金额、单次成功的链接规则；不保存分享令牌原文或付款敏感数据。 */
@Data
@TableName("payment_link")
public class PaymentLinkDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String linkId;
    private String merchantId;
    private String merchantOrderNo;
    private String linkName;
    private String description;
    /** ISO 4217 标签币种；金额以该币种主单位保存。 */
    private String currency;
    /** 创建时冻结，ISO 精度严格校验，不以浮点或自动舍入接受超精度输入。 */
    private BigDecimal amount;
    private Integer currencyExponent;
    private String locale;
    private String allowedBrandsJson;
    /** 带版本的冻结支付方式范围，历史链接为空。 */
    private String allowedMethodsJson;
    private String merchantDisplayName;
    private String notifyUrl;
    private String redirectUrl;
    private String status;
    private LocalDateTime expireTime;
    private String createRequestId;
    private String requestFingerprint;
    private String createdAppCode;
    private String createdBy;
    /** 停用主体为可信应用编码与账号 ID，不能与另一应用同号账号混淆。 */
    private String disabledBy;
    private String disableRequestId;
    private String disableReason;
    private LocalDateTime disabledTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
