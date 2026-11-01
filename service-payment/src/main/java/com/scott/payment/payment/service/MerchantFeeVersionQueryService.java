package com.scott.payment.payment.service;

import com.scott.payment.payment.entity.MerchantFeeVersionPointerDO;
import com.scott.payment.payment.service.dto.MerchantFeeVersionConfigurationDTO;

/**
 * Payment 费用配置只读边界，使用独立只读事务从主库选择当前版本，并按版本从 Slave、Master 加载不可变配置。
 */
public interface MerchantFeeVersionQueryService {

    /**
     * 从主库读取商户当前 ACTIVE 费用版本指针。
     *
     * @param merchantId 平台商户号
     * @return 当前版本身份；无有效 MERCHANT 配置时返回 null
     */
    MerchantFeeVersionPointerDO findActivePointerFromMaster(String merchantId);

    /**
     * 从 Slave 按已锁定版本读取完整不可变配置。
     *
     * @return 完整配置；从库尚未同步时返回 null
     */
    MerchantFeeVersionConfigurationDTO findVersionFromSlave(String merchantId,
                                                            Long feePlanId,
                                                            Long feePlanVersionId);

    /**
     * 从 Master 按已锁定版本读取完整不可变配置，作为 Slave 缺失或异常时的强一致回源。
     *
     * @return 完整配置；事实数据不存在时返回 null
     */
    MerchantFeeVersionConfigurationDTO findVersionFromMaster(String merchantId,
                                                             Long feePlanId,
                                                             Long feePlanVersionId);
}
