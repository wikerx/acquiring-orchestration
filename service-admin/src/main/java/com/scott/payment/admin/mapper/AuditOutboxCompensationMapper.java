package com.scott.payment.admin.mapper;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 非资金审计 Outbox 补偿盘点 Mapper，只查询满足严格门槛的 Admin 操作日志候选及最终幂等事实。
 */
public interface AuditOutboxCompensationMapper {

    /**
     * 查询最早一批仍缺少最终数据库事实的 Admin 操作日志 CLOSED 消息。
     *
     * <p>SQL 同时锁定生产服务、Topic、消息声明来源、合法 JSON、重试耗尽状态和最终幂等事实缺失；
     * 不返回或记录载荷之外的任何业务字段。</p>
     *
     * @param limit 查询数量，调用方还会执行不超过 5 的硬上限校验
     * @return 按原始创建时间排序的补偿候选
     */
    @Select("""
            SELECT outbox.*
            FROM sys_mq_outbox outbox
            LEFT JOIN sys_oper_log operation_log
              ON operation_log.idempotent_key =
                 CASE
                   WHEN CHAR_LENGTH(
                     COALESCE(
                       NULLIF(JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.idempotentKey')), 'null'),
                       CONCAT(
                         JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.systemCode')),
                         ':',
                         JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.messageId'))
                       )
                     )
                   ) <= 255
                   THEN COALESCE(
                     NULLIF(JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.idempotentKey')), 'null'),
                     CONCAT(
                       JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.systemCode')),
                       ':',
                       JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.messageId'))
                     )
                   )
                   ELSE CONCAT(
                     JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.systemCode')),
                     ':sha256:',
                     LOWER(SHA2(
                       COALESCE(
                         NULLIF(JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.idempotentKey')), 'null'),
                         CONCAT(
                           JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.systemCode')),
                           ':',
                           JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.messageId'))
                         )
                       ),
                       256
                     ))
                   )
                 END
            WHERE outbox.event_status = 'CLOSED'
              AND outbox.producer_service = 'service-admin'
              AND outbox.topic = 'acquiring_admin_operation_log_topic'
              AND outbox.tag IS NULL
              AND outbox.retry_count >= outbox.max_retry_count
              AND JSON_VALID(outbox.payload_json) = 1
              AND JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.systemCode')) = 'ADMIN'
              AND NULLIF(JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.messageId')), 'null') IS NOT NULL
              AND operation_log.id IS NULL
            ORDER BY outbox.create_time ASC, outbox.id ASC
            LIMIT #{limit}
            """)
    List<ReliableMqOutboxDO> selectMissingAdminOperationLogs(@Param("limit") int limit);

    /**
     * 查询操作日志最终幂等事实数量。
     *
     * @param idempotentKey 归一化后的数据库幂等键
     * @return 0 或 1
     */
    @Select("SELECT COUNT(1) FROM sys_oper_log WHERE idempotent_key = #{idempotentKey}")
    int countOperationLogFact(@Param("idempotentKey") String idempotentKey);
}
