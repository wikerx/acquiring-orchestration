package com.scott.payment.clearing.dto;

import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeVersionConfigurationDTO
 * @date : 2026-08-26 09:55
 * @email : scott_x@163.com
 * @description : 清分按确切版本读取的不可变费用配置载体，不包含动作金额、汇率、余额或持卡人信息。
 * @status : create
 * @param merchantId 版本所属商户
 * @param feePlanId MERCHANT 费用方案 ID
 * @param feePlanVersionId 不可变版本 ID
 * @param feePlanVersionNo 方案内版本号
 * @param settlementCurrency 目标结算币种；清分不据此换汇
 * @param reserveRate 标签币种保证金比例
 * @param reserveDelayUnit 保证金留存周期单位
 * @param reserveDelayDays 保证金留存天数
 * @param rules 版本内全部原子费用规则
 */
public record FeeVersionConfigurationDTO(String merchantId,
                                         Long feePlanId,
                                         Long feePlanVersionId,
                                         int feePlanVersionNo,
                                         String settlementCurrency,
                                         BigDecimal reserveRate,
                                         String reserveDelayUnit,
                                         int reserveDelayDays,
                                         List<FeeRuleConfigurationSnapshot> rules) {

    public FeeVersionConfigurationDTO {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
