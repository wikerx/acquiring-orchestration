package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecalculateRequest;

/** 未结算动作清分重算边界；实现必须保留旧修订并原子切换结算候选。 */
public interface ClearingRecalculationService {

    ClearingCommandResponse recalculate(String transactionId, ClearingRecalculateRequest request);
}
