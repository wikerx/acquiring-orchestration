package com.scott.payment.merchant.mapper;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商户端非资金审计 Outbox 补偿盘点 Mapper，只查询满足严格门槛的 Merchant 操作日志候选。
 */
public interface AuditOutboxCompensationMapper {

    /**
     * 查询最早一批仍缺少最终数据库事实的 Merchant 操作日志 CLOSED 消息。
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
              AND outbox.producer_service = 'service-merchant'
              AND outbox.topic = 'acquiring_merchant_operation_log_topic'
              AND outbox.tag IS NULL
              AND outbox.retry_count >= outbox.max_retry_count
              AND JSON_VALID(outbox.payload_json) = 1
              AND JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.systemCode')) = 'MERCHANT'
              AND NULLIF(JSON_UNQUOTE(JSON_EXTRACT(outbox.payload_json, '$.messageId')), 'null') IS NOT NULL
              AND operation_log.id IS NULL
            ORDER BY outbox.create_time ASC, outbox.id ASC
            LIMIT #{limit}
            """)
    List<ReliableMqOutboxDO> selectMissingMerchantOperationLogs(@Param("limit") int limit);

    /** 查询操作日志最终幂等事实数量。 */
    @Select("SELECT COUNT(1) FROM sys_oper_log WHERE idempotent_key = #{idempotentKey}")
    int countOperationLogFact(@Param("idempotentKey") String idempotentKey);
}
