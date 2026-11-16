package com.scott.payment.admin.client.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台调用 service-payment 发起交易后续动作的内部客户端请求，位于 service-admin 客户端层，仅承载退款、撤销等后台动作必要上下文。
 */
@Data
public class PaymentTransactionActionClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台商户号，用于支付核心校验交易归属和构建幂等键。
     */
    private String merchantId;

    /**
     * 商户订单号，后台动作按原交易主单回填，保持交易生命周期一致。
     */
    private String merchantOrderNo;

    /**
     * 本次后台动作唯一请求号，用于支付核心幂等保护。
     */
    private String merchantOrderId;

    /**
     * 交易类型，由 service-payment 内部接口按入口强制设置。
     */
    private String transactionType;

    /**
     * 商户标签口径的动作金额；渠道交易金额由支付核心按原交易冻结汇率计算。
     */
    private BigDecimal amount;

    /**
     * 页面标签币种金额，供支付核心保留商户/后台展示口径。
     */
    private BigDecimal labelAmount;

    /**
     * 页面标签币种，供支付核心保留商户/后台展示口径。
     */
    private String labelCurrency;

    /**
     * 商户标签口径的动作币种；必须与原交易标签币种一致。
     */
    private String currency;

    /**
     * 后台动作交易时间，对应分表字段 transaction_date_time。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /**
     * 请求追踪号，当前与 merchantOrderId 保持一致。
     */
    private String requestId;

    /**
     * 内部动作来源，管理后台固定为 ADMIN_PORTAL。
     */
    private String requestSource;

    /**
     * 当前认证后台账号 ID。
     */
    private String applicantId;

    /**
     * 当前认证后台账号显示名快照。
     */
    private String applicantName;

    /**
     * 后台填写的退款或撤销原因。
     */
    private String requestReason;

    /**
     * 交易扩展信息，用于传递原平台交易 ID 和后台操作说明。
     */
    private TransactionInfoDTO transactionInfo;

    /**
     * Admin 发起退款、撤销、请款等内部交易命令时传递的来源交易和操作说明上下文。
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 原平台交易 ID，支付核心会据此定位原交易分表。
         */
        private String sourceTransactionId;

        /**
         * 源动作真实分片时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;

        /**
         * 生命周期根主单真实分片时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 后台操作原因，进入交易描述和审计上下文。
         */
        private String description;
    }
}
