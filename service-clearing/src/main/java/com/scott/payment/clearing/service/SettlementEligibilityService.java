package com.scott.payment.clearing.service;

import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;

import java.time.LocalDate;

/** 根据交易冻结的结算政策计算最早可结算业务日期。 */
public interface SettlementEligibilityService {

    LocalDate calculate(String merchantId, LocalDate transactionDate, FeeVersionSnapshot snapshot);
}
