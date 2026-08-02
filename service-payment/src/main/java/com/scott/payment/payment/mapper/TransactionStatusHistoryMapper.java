package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionStatusHistoryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionStatusHistoryMapper
 * @date : 2026-07-14 17:40
 * @email : scott_x@163.com
 * @description : 交易状态历史 Mapper，位于 service-payment 数据访问层，仅负责 transaction_status_history 逻辑表及物理分表访问。
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

    /**
     * 按生命周期和半开交易时间范围查询状态历史。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 状态历史列表
     */
    @Select("""
            SELECT *
            FROM transaction_status_history
            WHERE operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
            ORDER BY status_time ASC, id ASC
            LIMIT 500
            """)
    List<TransactionStatusHistoryDO> selectByOperationId(@Param("operationId") String operationId,
                                                         @Param("beginTime") LocalDateTime beginTime,
                                                         @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 写入交易状态历史物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param historyDO         状态历史记录
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
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
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       @Param("historyDO") TransactionStatusHistoryDO historyDO);

    /**
     * 按 operation_id 查询生命周期状态历史。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId 平台内部生命周期关联标识
     * @return 状态历史列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
            ORDER BY status_time ASC, id ASC
            LIMIT 500
            """)
    List<TransactionStatusHistoryDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                 @Param("operationId") String operationId);
}
