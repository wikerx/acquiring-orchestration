package com.scott.payment.clearing.entity;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingMerchantSettlementProfileDO
 * @date : 2026-08-26 18:36
 * @email : scott_x@163.com
 * @description : 保证金独立释放候选所需的活动结算目标只读投影；不承载费用、汇率或资金账户写入。
 * @status : create
 */
@Data
public class ClearingMerchantSettlementProfileDO {
    /** 活动结算档案主键。 */
    private Long id;
    /** 档案所属平台商户号。 */
    private String merchantId;
    /** 独立释放候选未来进入结算批次的目标币种。 */
    private String targetCurrency;
    /** 目标币种 ISO exponent。 */
    private Integer targetCurrencyExponent;
}
