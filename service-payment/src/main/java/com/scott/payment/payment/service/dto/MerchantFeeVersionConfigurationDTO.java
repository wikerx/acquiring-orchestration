package com.scott.payment.payment.service.dto;

import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeVersionConfigurationDTO
 * @date : 2026-08-25 22:40
 * @email : scott_x@163.com
 * @description : Payment 内部不可变费用版本配置，作为数据库聚合结果和 fee:version 缓存值的业务载体，不包含动作时间、汇率或持卡人信息。
 * @status : create
 * @param merchantId 费用版本所属平台商户号
 * @param feePlanId MERCHANT 费用方案主键
 * @param feePlanVersionId 不可变版本主键
 * @param feePlanVersionNo 方案内版本号
 * @param settlementCurrency 商户目标结算币种；清分阶段不换汇
 * @param reserveRate 标签金额保证金百分比
 * @param reserveDelayUnit 保证金留存周期单位 D 或 T
 * @param reserveDelayDays 保证金留存天数
 * @param rules 当前版本全部原子规则及阶梯
 */
public record MerchantFeeVersionConfigurationDTO(String merchantId,
                                                 Long feePlanId,
                                                 Long feePlanVersionId,
                                                 int feePlanVersionNo,
                                                 String settlementCurrency,
                                                 BigDecimal reserveRate,
                                                 String reserveDelayUnit,
                                                 int reserveDelayDays,
                                                 List<FeeRuleConfigurationSnapshot> rules) {

    public MerchantFeeVersionConfigurationDTO {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
