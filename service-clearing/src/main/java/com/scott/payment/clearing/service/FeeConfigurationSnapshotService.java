package com.scott.payment.clearing.service;

import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeConfigurationSnapshotService
 * @date : 2026-08-26 09:55
 * @email : scott_x@163.com
 * @description : 清分事务外费用快照加载边界，优先动作冻结 JSON，按确切版本 Redis、Slave、Master 降级并校验 SHA-256。
 * @status : create
 */
public interface FeeConfigurationSnapshotService {

    /**
     * 加载并验证当前动作受理时冻结的不可变费用版本。
     *
     * @param merchantId 数据库动作商户号
     * @param operationId 数据库动作生命周期号
     * @param transactionId 动作级交易号
     * @param transactionDateTime 动作季度分片时间
     * @return 身份和摘要均验证通过的费用版本
     */
    FeeVersionSnapshot load(String merchantId,
                            String operationId,
                            String transactionId,
                            LocalDateTime transactionDateTime);

    /**
     * 人工重算按明确方案和版本加载不可变配置并形成新的计算快照；不修改动作受理时的原快照。
     */
    FeeVersionSnapshot loadForRecalculation(String merchantId,
                                            Long feePlanId,
                                            Long feePlanVersionId,
                                            LocalDateTime pricingLockTime);
}
