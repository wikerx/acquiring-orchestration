package com.scott.payment.clearing.service;

import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;

import java.time.LocalDate;

/**
 * 根据交易冻结的结算政策计算最早可结算业务日期。
 */
public interface SettlementEligibilityService {

    /**
     * 使用交易冻结的结算周期和已确认工作日日历计算最早结算日。
     */
    LocalDate calculate(String merchantId, LocalDate transactionDate, FeeVersionSnapshot snapshot);
}
