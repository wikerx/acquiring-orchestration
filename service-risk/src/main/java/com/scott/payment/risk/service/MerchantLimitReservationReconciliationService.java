package com.scott.payment.risk.service;

import com.scott.payment.risk.domain.MerchantLimitReservationReconciliationSummary;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationReconciliationService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额预占超时自愈服务。
 * @status : create
 */
public interface MerchantLimitReservationReconciliationService {

    /**
     * 对账超时非终态预占，按支付事实状态执行确认、取消或保留。
     *
     * @param now   本轮对账时间
     * @param limit 单批最大交易数
     * @return 恢复、取消和保留数量
     */
    MerchantLimitReservationReconciliationSummary reconcile(
            LocalDateTime now,
            int limit);
}
