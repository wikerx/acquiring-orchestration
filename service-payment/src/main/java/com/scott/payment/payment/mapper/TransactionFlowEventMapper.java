package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionFlowEventDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionFlowEventMapper
 * @date : 2026-07-14 19:44
 * @email : scott_x@163.com
 * @description : 交易流程事件 Mapper，位于 service-payment 数据访问层，仅负责 transaction_flow_event 物理分表写入。
 * @status : create
 */
public interface TransactionFlowEventMapper extends BaseMapper<TransactionFlowEventDO> {

    /**
     * 写入交易流程事件物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param eventDO           流程事件
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
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
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       /**
                        * 完成 m 分支的校验或状态更新。
                        * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                        * <p>
                        * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                        * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                        * </p>
                        * @param eventDO event DO 输入值，含义由调用方法名称和所属业务对象限定
                        */
                       @Param("eventDO") TransactionFlowEventDO eventDO);

    /**
     * 按平台交易 ID 查询交易流程事件。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @return 流程事件列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
            ORDER BY event_time ASC, id ASC
            LIMIT 200
            """)
    List<TransactionFlowEventDO> selectByTransactionIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                               /**
                                                                * 完成 m 分支的校验或状态更新。
                                                                * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                * <p>
                                                                * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                * </p>
                                                                * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
                                                                */
                                                               @Param("transactionId") String transactionId);

    /**
     * 按 operation_id 查询交易生命周期流程事件。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId 平台内部生命周期关联标识
     * @return 流程事件列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
            ORDER BY event_time ASC, id ASC
            LIMIT 500
            """)
    List<TransactionFlowEventDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                             /**
                                                              * 完成 m 分支的校验或状态更新。
                                                              * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                              * <p>
                                                              * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                              * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                              * </p>
                                                              * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
                                                              */
                                                             @Param("operationId") String operationId);
}
