package com.scott.payment.payment.service.dto;

import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payment 内部不可变费用版本配置，作为数据库聚合结果和 fee:version 缓存值的业务载体，不包含动作时间、汇率或持卡人信息。
 */
public record MerchantFeeVersionConfigurationDTO(String merchantId,
                                                 Long feePlanId,
                                                 Long feePlanVersionId,
                                                 int feePlanVersionNo,
                                                 String settlementCurrency,
                                                 String settlementDelayUnit,
                                                 int initialSettlementDelayDays,
                                                 int regularSettlementDelayDays,
                                                 String settlementFrequency,
                                                 Integer settlementFrequencyDay,
                                                 LocalDate settlementFrequencyAnchorDate,
                                                 BigDecimal reserveRate,
                                                 String reserveDelayUnit,
                                                 int reserveDelayDays,
                                                 List<FeeRuleConfigurationSnapshot> rules) {

    /**
     * 创建当前数据对象。
     */
    public MerchantFeeVersionConfigurationDTO {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
