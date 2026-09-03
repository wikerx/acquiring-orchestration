package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFundAccountDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 结算侧资金账户锁读投影；入账仅接受 NORMAL 账户，并由结算资金服务以余额和 accountVersion 双条件 CAS 更新。
 * @status : create
 */
@Data
public class MerchantFundAccountDO {
    /** 资金账户数据库主键，不允许为空。 */
    private Long id;
    /** 对运营展示的资金账户号，不允许为空。 */
    private String accountNo;
    /** 账户所属平台商户号，不允许为空。 */
    private String merchantId;
    /** 账户 ISO 结算币种，必须与批次目标币种一致。 */
    private String settlementCurrency;
    /** 当前可用余额，单位由 settlementCurrency 的 ISO exponent 决定。 */
    private BigDecimal availableBalance;
    /** 账户可用状态；结算入账只允许 NORMAL，FROZEN 不等同于可入账。 */
    private String accountStatus;
    /** 余额更新乐观锁版本，不允许为空。 */
    private Long accountVersion;
}
