package com.scott.payment.clearing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionOperationMapper
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 清分动作只读 Mapper，按 transaction_id 与真实季度分片时间精确读取数据库权威终态。
 * @status : create
 */
public interface ClearingTransactionOperationMapper extends BaseMapper<ClearingTransactionOperationDO> {

    /**
     * 精确读取当前动作事实，调用方必须另外校验消息中的商户和生命周期身份。
     *
     * @param transactionId 动作级平台交易号
     * @param transactionDateTime 动作季度分片时间
     * @return 当前动作；不存在时返回 null
     */
    @Select("""
            SELECT id, transaction_id, operation_id, source_transaction_id,
                   merchant_id, merchant_order_no, transaction_type, transaction_status,
                   label_currency, label_amount, approved_currency, approved_amount,
                   transaction_currency, transaction_amount, currency_exponent,
                   transaction_date_time, transaction_utc_time, transaction_time_zone, version
            FROM transaction_operation
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    ClearingTransactionOperationDO selectByTransaction(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /** 使用动作分片时间、当前版本和投影当前状态 CAS 更新清分查询投影。 */
    @Update("""
            UPDATE transaction_operation
            SET clearing_status = #{clearingStatus},
                clearing_complete_time = #{completeTime},
                clearing_failure_code = #{failureCode},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND clearing_status IN ('NOT_CLEARED', 'PENDING', 'FAILED')
              AND deleted = 0
            """)
    int updateClearingProjection(@Param("transactionId") String transactionId,
                                 @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                 @Param("expectedVersion") int expectedVersion,
                                 @Param("clearingStatus") String clearingStatus,
                                 @Param("completeTime") LocalDateTime completeTime,
                                 @Param("failureCode") String failureCode,
                                 @Param("now") LocalDateTime now);
}
