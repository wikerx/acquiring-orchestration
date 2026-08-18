package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionMerchantSnapshotDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantSnapshotMapper
 * @date : 2026-08-14 16:00
 * @email : scott_x@163.com
 * @description : 交易商户快照 Mapper，仅按平台交易号和真实分片时间访问 transaction_merchant_snapshot 逻辑表。
 * @status : create
 */
public interface TransactionMerchantSnapshotMapper extends BaseMapper<TransactionMerchantSnapshotDO> {

    /**
     * 查询首次交易冻结的商户快照。
     *
     * @param merchantId 商户号
     * @param transactionId 生命周期根交易号
     * @param transactionDateTime 生命周期根交易分片时间
     * @return 商户快照，不存在时返回 null
     */
    @Select("""
            SELECT id, snapshot_id, transaction_id, operation_id, merchant_id,
                   sub_merchant_info_json,
                   merchant_name, merchant_country, merchant_category_code, merchant_status,
                   channel_id, channel_code, channel_mid_config_id, channel_merchant_id, terminal_id,
                   channel_mid_metadata_json, settlement_config_snapshot_json, fee_config_snapshot_json,
                   internal_risk_config_snapshot_json, route_config_snapshot_json,
                   transaction_date_time, transaction_utc_time, transaction_time_zone,
                   create_time, update_time
            FROM transaction_merchant_snapshot
            WHERE merchant_id = #{merchantId}
              AND transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            LIMIT 1
            """)
    TransactionMerchantSnapshotDO selectByTransaction(@Param("merchantId") String merchantId,
                                                      @Param("transactionId") String transactionId,
                                                      @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
