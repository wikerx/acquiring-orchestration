package com.scott.payment.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 系统交易加黑批次与逐候选审计 Mapper。 */
@Mapper
public interface AdminTradeBlackWorkflowMapper {

    /** 创建幂等操作批次。 */
    @Insert("""
            INSERT INTO risk_trade_black_batch (
                batch_no, request_id, request_fingerprint, input_mode, scope_type, action_type,
                function_count, order_count, status, operator_account_id, operator, deleted
            ) VALUES (
                #{data.batchNo}, #{data.requestId}, #{data.requestFingerprint}, #{data.inputMode}, #{data.scopeType}, #{data.actionType},
                #{data.functionCount}, #{data.orderCount}, 'PROCESSING', #{operatorAccountId}, #{operator}, 0
            )
            """)
    int insertBatch(@Param("data") Map<String, Object> data,
                    @Param("operatorAccountId") Long operatorAccountId,
                    @Param("operator") String operator);

    /** 按当前操作账号和请求幂等号读取现有批次。 */
    @Select("""
            SELECT batch_no, request_id, request_fingerprint, status,
                   total_count, success_count, skipped_count, failure_count
            FROM risk_trade_black_batch
            WHERE operator_account_id = #{operatorAccountId}
              AND request_id = #{requestId}
              AND deleted = 0
            LIMIT 1
            """)
    Map<String, Object> selectBatchByRequestId(@Param("requestId") String requestId,
                                               @Param("operatorAccountId") Long operatorAccountId);

    /** 校验指定批次是否属于当前操作账号。 */
    @Select("""
            SELECT COUNT(1)
            FROM risk_trade_black_batch
            WHERE batch_no = #{batchNo}
              AND operator_account_id = #{operatorAccountId}
              AND deleted = 0
            """)
    int countBatchForAccount(@Param("batchNo") String batchNo,
                             @Param("operatorAccountId") Long operatorAccountId);

    /** 查询指定批次的失败明细；只返回脱敏值和审计字段。 */
    @Select("""
            SELECT item.row_no,
                   item.input_order_no,
                   item.function_code,
                   item.source_field,
                   item.target_scope,
                   item.target_merchant_id,
                   item.match_value_masked,
                   item.result_code,
                   item.result_message
            FROM risk_trade_black_batch_item item
            INNER JOIN risk_trade_black_batch batch ON batch.batch_no = item.batch_no
            WHERE item.batch_no = #{batchNo}
              AND batch.operator_account_id = #{operatorAccountId}
              AND item.result_code = 'FAILED'
              AND item.deleted = 0
              AND batch.deleted = 0
            ORDER BY item.row_no ASC, item.id ASC
            """)
    List<Map<String, Object>> selectFailedItems(@Param("batchNo") String batchNo,
                                                @Param("operatorAccountId") Long operatorAccountId);

    /** 完成批次并写入聚合结果。 */
    @Update("""
            UPDATE risk_trade_black_batch
            SET status = #{status},
                total_count = #{totalCount},
                success_count = #{successCount},
                skipped_count = #{skippedCount},
                failure_count = #{failureCount},
                finish_time = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE batch_no = #{batchNo} AND deleted = 0
            """)
    int finishBatch(@Param("batchNo") String batchNo,
                    @Param("status") String status,
                    @Param("totalCount") int totalCount,
                    @Param("successCount") int successCount,
                    @Param("skippedCount") int skippedCount,
                    @Param("failureCount") int failureCount);

    /** 写入候选项级审计；不保存敏感明文或名单哈希。 */
    @Insert("""
            INSERT INTO risk_trade_black_batch_item (
                batch_no, candidate_key, row_no, input_order_no, transaction_id,
                source_merchant_id, merchant_order_no, function_code, function_name,
                source_field, target_scope, target_merchant_id, match_value_masked,
                result_code, result_message, operator, deleted
            ) VALUES (
                #{data.batchNo}, #{data.candidateKey}, #{data.rowNo}, #{data.inputOrderNo}, #{data.transactionId},
                #{data.sourceMerchantId}, #{data.merchantOrderNo}, #{data.functionCode}, #{data.functionName},
                #{data.sourceField}, #{data.targetScope}, #{data.targetMerchantId}, #{data.maskedValue},
                #{data.resultCode}, #{data.resultMessage}, #{operator}, 0
            )
            """)
    int insertItem(@Param("data") Map<String, Object> data, @Param("operator") String operator);
}
