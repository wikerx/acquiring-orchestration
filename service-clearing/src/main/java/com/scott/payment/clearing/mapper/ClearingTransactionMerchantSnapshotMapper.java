package com.scott.payment.clearing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.clearing.entity.ClearingTransactionMerchantSnapshotDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionMerchantSnapshotMapper
 * @date : 2026-08-26 09:55
 * @email : scott_x@163.com
 * @description : 清分动作费用快照 Mapper，所有季度表读取均显式携带 transaction_date_time。
 * @status : create
 */
public interface ClearingTransactionMerchantSnapshotMapper
        extends BaseMapper<ClearingTransactionMerchantSnapshotDO> {

    /**
     * 读取当前动作冻结的费用版本身份和 JSON。
     *
     * @param transactionId 动作级交易号
     * @param transactionDateTime 动作季度分片时间
     * @return 当前动作商户快照；不存在时返回 null
     */
    @Select("""
            SELECT id, snapshot_id, transaction_id, operation_id, merchant_id,
                   fee_config_snapshot_json, fee_plan_id, fee_plan_version_id,
                   fee_plan_version_no, fee_snapshot_hash, fee_snapshot_time,
                   transaction_date_time
            FROM transaction_merchant_snapshot
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            LIMIT 1
            """)
    ClearingTransactionMerchantSnapshotDO selectByTransaction(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
