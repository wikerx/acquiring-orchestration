package com.scott.payment.clearing.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionIdempotencyMapper
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 清分 MQ 消费幂等 Mapper，复用 transaction_idempotency 的数据库唯一键，不以 Redis 作为最终幂等。
 * @status : create
 */
public interface ClearingTransactionIdempotencyMapper {

    /**
     * 判断当前清分消息是否已经与阶段B同事务成功提交。
     *
     * @param idempotencyKey consumerGroup 与 messageId 组成的稳定键
     * @return 已存在未删除 SUCCESS 记录时为 true
     */
    @Select("""
            SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END
            FROM transaction_idempotency
            WHERE idempotency_scope = 'MQ_CONSUME_CLEARING'
              AND idempotency_key = #{idempotencyKey}
              AND transaction_status = 'SUCCESS'
              AND deleted = 0
            """)
    boolean existsSuccessfulConsumption(@Param("idempotencyKey") String idempotencyKey);

    /** 与阶段B清分事实同事务写入 SUCCESS 消费幂等，不保存金额或费用配置正文。 */
    @Insert("""
            INSERT INTO transaction_idempotency
            (idempotency_scope, idempotency_key, merchant_id, merchant_order_no,
             transaction_type, transaction_id, operation_id, transaction_status,
             transaction_date_time, transaction_utc_time, transaction_time_zone,
             result_snapshot, expire_time, version, deleted, create_time, update_time)
            VALUES
            ('MQ_CONSUME_CLEARING', #{idempotencyKey}, #{merchantId}, #{merchantOrderNo},
             #{transactionType}, #{transactionId}, #{operationId}, 'SUCCESS',
             #{transactionDateTime}, #{transactionUtcTime}, #{transactionTimeZone},
             #{resultSnapshot}, #{expireTime}, 0, 0, #{now}, #{now})
            """)
    int insertSuccessfulConsumption(@Param("idempotencyKey") String idempotencyKey,
                                    @Param("merchantId") String merchantId,
                                    @Param("merchantOrderNo") String merchantOrderNo,
                                    @Param("transactionType") String transactionType,
                                    @Param("transactionId") String transactionId,
                                    @Param("operationId") String operationId,
                                    @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                    @Param("transactionUtcTime") LocalDateTime transactionUtcTime,
                                    @Param("transactionTimeZone") String transactionTimeZone,
                                    @Param("resultSnapshot") String resultSnapshot,
                                    @Param("expireTime") LocalDateTime expireTime,
                                    @Param("now") LocalDateTime now);
}
