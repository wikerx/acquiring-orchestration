package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionStatusHistoryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionStatusHistoryMapper
 * @date : 2026-07-14 17:40
 * @email : scott_x@163.com
 * @description : 交易状态历史 Mapper，位于 service-payment 数据访问层，仅访问 transaction_status_history 逻辑表。
 * @status : create
 */
public interface TransactionStatusHistoryMapper extends BaseMapper<TransactionStatusHistoryDO> {

    /**
     * 写入交易状态历史逻辑表。
     *
     * @param historyDO 状态历史记录
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_status_history
            (
              status_history_id, transaction_id, operation_id, status_object, from_status,
              to_status, trigger_type, trigger_id, transition_result, fail_reason,
              version_before, version_after, status_time, transaction_date_time,
              transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{historyDO.statusHistoryId}, #{historyDO.transactionId}, #{historyDO.operationId},
              #{historyDO.statusObject}, #{historyDO.fromStatus}, #{historyDO.toStatus},
              #{historyDO.triggerType}, #{historyDO.triggerId}, #{historyDO.transitionResult},
              #{historyDO.failReason}, #{historyDO.versionBefore}, #{historyDO.versionAfter},
              #{historyDO.statusTime}, #{historyDO.transactionDateTime}, #{historyDO.transactionUtcTime},
              #{historyDO.transactionTimeZone}, #{historyDO.createTime}
            )
            """)
    int insertLogical(@Param("historyDO") TransactionStatusHistoryDO historyDO);

}
