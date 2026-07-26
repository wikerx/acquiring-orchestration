package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentQueryClientResponseDTO
 * @date : 2026-07-17 21:12
 * @email : scott_x@163.com
 * @description : OpenAPI 调用 service-payment 查询交易的内部响应，按商户订单聚合返回交易动作列表。
 * @status : create
 */
@Data
public class PaymentQueryClientResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String merchantId;

    private String merchantOrderNo;

    private String merchantOrderId;

    private BigDecimal orderAmount;

    private String orderCurrency;

    private BigDecimal totalAuthorizedAmount;

    private BigDecimal totalCapturedAmount;

    private BigDecimal totalRefundAmount;

    private BigDecimal totalAuthorizedCancelAmount;

    private BigDecimal totalRefuseAmount;

    private BigDecimal labelAmount;

    private String labelCurrency;

    private BigDecimal transactionAmount;

    private String transactionCurrency;

    private BigDecimal transactionRate;

    private String rateSource;

    private LocalDateTime rateTime;

    private BigDecimal settlementAmount;

    private String settlementCurrency;

    private String transactionTimeZone;

    private List<TransactionInfoDTO> transactionInfo = new ArrayList<>();

    /**
     * 查询返回的单笔交易动作摘要。
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String transactionId;

        private String sourceTransactionId;

        private String code;

        private String message;

        private String transactionType;

        private LocalDateTime transactionDateTime;

        private String paymentMethod;

        private String cardBrand;

        private String cardBin;

        private String authCode;

        private String arn;

        private String description;

        private String callbackUrl;
    }
}
