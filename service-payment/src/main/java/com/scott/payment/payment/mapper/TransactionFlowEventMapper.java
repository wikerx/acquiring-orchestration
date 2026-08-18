package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionFlowEventDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionFlowEventMapper
 * @date : 2026-07-14 19:44
 * @email : scott_x@163.com
 * @description : 交易流程事件 Mapper，位于 service-payment 数据访问层，仅访问 transaction_flow_event 逻辑表。
 * @status : create
 */
public interface TransactionFlowEventMapper extends BaseMapper<TransactionFlowEventDO> {

    /**
     * 写入交易流程事件逻辑表。
     *
     * @param eventDO 流程事件
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_flow_event
            (
              flow_event_id, transaction_id, operation_id, event_type, event_stage, event_status,
              event_name, event_content, previous_status, current_status, operator_type, operator_id,
              reference_type, reference_id, error_code, error_message, event_time, transaction_date_time,
              transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{eventDO.flowEventId}, #{eventDO.transactionId}, #{eventDO.operationId},
              #{eventDO.eventType}, #{eventDO.eventStage}, #{eventDO.eventStatus},
              #{eventDO.eventName}, #{eventDO.eventContent}, #{eventDO.previousStatus},
              #{eventDO.currentStatus}, #{eventDO.operatorType}, #{eventDO.operatorId},
              #{eventDO.referenceType}, #{eventDO.referenceId}, #{eventDO.errorCode},
              #{eventDO.errorMessage}, #{eventDO.eventTime}, #{eventDO.transactionDateTime},
              #{eventDO.transactionUtcTime}, #{eventDO.transactionTimeZone}, #{eventDO.createTime}
            )
            """)
    int insertLogical(@Param("eventDO") TransactionFlowEventDO eventDO);

}
