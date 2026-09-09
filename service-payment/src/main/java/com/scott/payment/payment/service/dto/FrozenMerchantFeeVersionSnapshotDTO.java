package com.scott.payment.payment.service.dto;

import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;

import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FrozenMerchantFeeVersionSnapshotDTO
 * @date : 2026-08-25 22:45
 * @email : scott_x@163.com
 * @description : Payment 动作受理时冻结的费用版本及其规范化 JSON，供交易快照结构化列和 JSON 列在同一事务中写入。
 * @status : create
 * @param snapshot 已校验商户、版本、币种口径和 SHA-256 的不可变快照
 * @param snapshotJson 包含 snapshotHash 的规范化完整 JSON
 */
public record FrozenMerchantFeeVersionSnapshotDTO(FeeVersionSnapshot snapshot,
                                                  String snapshotJson) {

    public FrozenMerchantFeeVersionSnapshotDTO {
        Objects.requireNonNull(snapshot, "fee version snapshot is required");
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("fee version snapshot JSON is required");
        }
    }
}
