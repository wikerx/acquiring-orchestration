package com.scott.payment.settlement.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementOperationIdentityDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 交易动作投影和结算完成消息所需的最小非敏感身份；不包含卡号、密钥或渠道原始报文。
 * @status : create
 */
@Data
public class SettlementOperationIdentityDO {
    /** 平台交易主单号，不允许为空。 */
    private String transactionId;
    /** 真实清分修订对应的交易动作单号，不允许为空。 */
    private String operationId;
    /** 交易所属平台商户号。 */
    private String merchantId;
    /** 商户订单号，用于结算事件关联。 */
    private String merchantOrderNo;
    /** 平台统一交易类型，不使用渠道原始状态。 */
    private String transactionType;
    /** 投影前平台交易状态。 */
    private String transactionStatus;
    /** 原交易 ISO 币种。 */
    private String transactionCurrency;
    /** 投影前结算状态。 */
    private String settlementStatus;
    /** 根交易分片时间，查询交易分表时不允许为空。 */
    private LocalDateTime rootTransactionDateTime;
}
