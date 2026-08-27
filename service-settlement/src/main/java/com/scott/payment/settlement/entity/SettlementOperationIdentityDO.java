package com.scott.payment.settlement.entity;

import lombok.Data;

/** 交易动作投影和结算完成消息所需的最小非敏感身份。 */
@Data
public class SettlementOperationIdentityDO {
    private String transactionId;
    private String operationId;
    private String merchantId;
    private String merchantOrderNo;
    private String transactionType;
    private String transactionStatus;
    private String settlementStatus;
}
