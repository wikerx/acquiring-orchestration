package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculationOptionsResponse;

/** 清分重算费用版本只读边界，只暴露可用于重算的不可变版本描述。 */
public interface AdminClearingFeeVersionQueryService {

    /**
     * 查询商户费用方案的 ACTIVE 和 SUPERSEDED 版本。
     *
     * @param merchantId 清分记录所属商户
     * @param feePlanId 清分记录当前费用方案
     * @return 方案描述和可重算版本
     */
    RecalculationOptionsResponse listOptions(String merchantId, Long feePlanId);
}
