package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.FrozenMerchantFeeVersionSnapshotDTO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeVersionSnapshotService
 * @date : 2026-08-25 22:45
 * @email : scott_x@163.com
 * @description : Payment 动作费用冻结服务，选择商户当前 MERCHANT 生效版本，校验不可变缓存并生成动作级 hash；不计算费用、汇率或余额。
 * @status : create
 */
public interface MerchantFeeVersionSnapshotService {

    /**
     * 冻结动作受理时生效的完整费用版本；没有合法配置时 fail-closed，中断新动作受理。
     *
     * @param merchantId 当前交易所属平台商户号
     * @param pricingLockTime 动作受理时系统时间，将规范化为毫秒精度
     * @return 可同时写入结构化列和 JSON 列的费用快照
     */
    FrozenMerchantFeeVersionSnapshotDTO freezeActiveVersion(String merchantId,
                                                            LocalDateTime pricingLockTime);
}
