package com.scott.payment.settlement.service;

import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.dto.SettlementCurrency;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementRateResolutionService
 * @date : 2026-08-26 22:40
 * @email : scott_x@163.com
 * @description : 批次统一汇率解析边界，批量选择直接、反向或系统恒等报价并返回可持久化不可变矩阵。
 * @status : create
 */
public interface SettlementRateResolutionService {

    /**
     * 解析全部原币种到单一目标币种的完整矩阵。
     *
     * @param currencies 原币种和 ISO exponent 集合
     * @param targetCurrency 批次目标币种
     * @param targetCurrencyExponent 目标币种 exponent
     * @param valuationTime 本批统一估值时间
     * @return 可持久化锁定矩阵
     */
    RateMatrix resolve(Set<SettlementCurrency> currencies,
                       String targetCurrency,
                       int targetCurrencyExponent,
                       LocalDateTime valuationTime);
}
