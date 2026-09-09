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
     * 准备{@code freezeActiveVersion}，在执行外部动作前冻结必要事实并完成幂等与状态校验。
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param pricingLockTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法生成的 {@code FrozenMerchantFeeVersionSnapshotDTO} 结果
     */
    FrozenMerchantFeeVersionSnapshotDTO freezeActiveVersion(String merchantId,
                                                            LocalDateTime pricingLockTime);
}
