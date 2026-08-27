package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;

/** 结算侧资金账户锁读投影；只允许由结算资金入账服务更新余额和版本。 */
@Data
public class MerchantFundAccountDO {
    private Long id;
    private String accountNo;
    private String merchantId;
    private String settlementCurrency;
    private BigDecimal availableBalance;
    private String accountStatus;
    private Long accountVersion;
}
