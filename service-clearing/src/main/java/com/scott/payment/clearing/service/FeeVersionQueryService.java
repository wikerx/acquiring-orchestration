package com.scott.payment.clearing.service;

import com.scott.payment.clearing.dto.FeeVersionConfigurationDTO;

/**
 * 清分费用版本主从只读边界，只按动作冻结的商户、方案 ID 和版本 ID 查询不可变配置。
 */
public interface FeeVersionQueryService {

    /**
     * 按明确版本从 Slave 读取，不查询活动版本指针。
     */
    FeeVersionConfigurationDTO findVersionFromSlave(String merchantId,
                                                    Long feePlanId,
                                                    Long feePlanVersionId);

    /**
     * Slave 不可见时按同一版本从 Master 强一致回源。
     */
    FeeVersionConfigurationDTO findVersionFromMaster(String merchantId,
                                                     Long feePlanId,
                                                     Long feePlanVersionId);
}
