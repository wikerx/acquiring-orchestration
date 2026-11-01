package com.scott.payment.payment.service.dto;

import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;

import java.util.Objects;

/**
 * Payment 动作受理时冻结的费用版本及其规范化 JSON，供交易快照结构化列和 JSON 列在同一事务中写入。
 */
public record FrozenMerchantFeeVersionSnapshotDTO(FeeVersionSnapshot snapshot,
                                                  String snapshotJson) {

    /**
     * 创建当前数据对象。
     */
    public FrozenMerchantFeeVersionSnapshotDTO {
        Objects.requireNonNull(snapshot, "fee version snapshot is required");
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("fee version snapshot JSON is required");
        }
    }
}
