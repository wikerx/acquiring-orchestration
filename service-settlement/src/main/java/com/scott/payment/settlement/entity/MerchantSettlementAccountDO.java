package com.scott.payment.settlement.entity;

import lombok.Data;

/**
 * 结算档案命令校验所需的最小资金账户投影，不承载余额变更行为。
 */
@Data
public class MerchantSettlementAccountDO {

    /**
     * 资金账户主键。
     */
    private Long id;
    /**
     * 资金账户号。
     */
    private String accountNo;
    /**
     * 账户所属商户号。
     */
    private String merchantId;
    /**
     * 账户唯一结算币种。
     */
    private String settlementCurrency;
    /**
     * NORMAL、FROZEN 或 CLOSED。
     */
    private String accountStatus;
}
