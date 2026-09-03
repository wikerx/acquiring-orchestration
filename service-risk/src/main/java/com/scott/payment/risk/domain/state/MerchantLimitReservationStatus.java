package com.scott.payment.risk.domain.state;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationStatus
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额预占生命周期状态。
 * @status : create
 */
public enum MerchantLimitReservationStatus {

    /** 持久记录已创建，Redis 原子预占尚未确认成功。 */
    PREPARING,

    /** Redis 预占成功，等待支付交易进入明确终态。 */
    RESERVED,

    /** 支付成功后确认的终态，预占金额正式计入累计限额。 */
    CONFIRMED,

    /** 支付失败或补偿回滚后的终态，禁止再次转为 RESERVED 或 CONFIRMED。 */
    CANCELLED;

    /**
     * 判断当前状态是否不可逆。
     *
     * @return true 表示已经进入终态
     */
    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED;
    }
}
