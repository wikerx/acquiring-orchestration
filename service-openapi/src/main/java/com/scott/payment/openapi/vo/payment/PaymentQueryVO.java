package com.scott.payment.openapi.vo.payment;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentQueryVO
 * @date : 2026-07-17 21:14
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 交易查询响应 VO，按商户订单返回关联交易动作列表，避免和创建类接口的单对象 transactionInfo 契约混用。
 * @status : create
 */
@Data
public class PaymentQueryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商户信息，按查询请求原样回显。
     */
    private PaymentCreateVO.MerchantInfoVO merchantInfo;

    /**
     * 商户订单查询摘要。
     */
    private PaymentCreateVO.OrderInfoVO orderInfo;

    /**
     * 同一商户订单下的交易动作列表。
     */
    private List<TransactionInfoVO> transactionInfo = new ArrayList<>();

    /**
     * 账单和换汇信息。
     */
    private PaymentCreateVO.BillingInfoVO billingInfo;

    /**
     * 查询返回的单笔交易动作摘要。
     */
    @Data
    public static class TransactionInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台当前交易唯一标识。
         */
        private String transactionId;

        /**
         * 原平台交易唯一标识，首次类交易为空。
         */
        private String sourceTransactionId;

        /**
         * 当前动作商户响应码。
         */
        private String code;

        /**
         * 当前动作商户响应描述。
         */
        private String message;

        /**
         * 交易类型，对齐系统字典 transaction_type。
         */
        private String transactionType;

        /**
         * 交易发生时间，按交易业务时区展示。
         */
        private OffsetDateTime transactionDateTime;

        /** 生命周期根主单时间，必须与后续动作请求一并回传。 */
        private OffsetDateTime rootTransactionDateTime;

        /**
         * 支付方式，如 BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 卡品牌或支付品牌，如 MASTERCARD、VISA。
         */
        private String cardBrand;

        /**
         * 脱敏卡 BIN，格式为前六位 + **** + 后四位。
         */
        private String cardBin;

        /**
         * 授权码，渠道成功返回时填写。
         */
        private String authCode;

        /**
         * ARN 或收单机构参考号。
         */
        private String arn;

        /**
         * 订单备注或描述。
         */
        private String description;

        /**
         * 商户通知回调地址。
         */
        private String callbackUrl;
    }
}
