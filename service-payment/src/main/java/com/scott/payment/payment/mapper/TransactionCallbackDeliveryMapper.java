package com.scott.payment.payment.mapper;

import com.scott.payment.payment.api.internal.dto.CallbackDeliveryResponseCommand;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 渠道回调逐次投递审计，与回调 Inbox 使用同一个交易数据源。 */
@Mapper
public interface TransactionCallbackDeliveryMapper {

    /** 同一个内部请求重试时保留首次受理结果。 */
    @Insert("""
            INSERT INTO transaction_callback_delivery
                (delivery_id, inbox_id, trace_id, received_time, signature_valid, accept_result,
                 request_body_sha256, request_body_masked)
            VALUES
                (#{deliveryId}, #{inboxId}, #{traceId}, #{receivedTime}, #{signatureValid}, #{acceptResult},
                 #{bodySha256}, #{requestBodyMasked})
            ON DUPLICATE KEY UPDATE delivery_id = delivery_id
            """)
    int record(@Param("deliveryId") String deliveryId,
               @Param("inboxId") String inboxId,
               @Param("traceId") String traceId,
               @Param("receivedTime") LocalDateTime receivedTime,
               @Param("signatureValid") boolean signatureValid,
               @Param("acceptResult") String acceptResult,
               @Param("bodySha256") String bodySha256,
               @Param("requestBodyMasked") String requestBodyMasked);

    /** 单次投递只保留第一次观测到的入口应答。 */
    @Update("""
            UPDATE transaction_callback_delivery
            SET http_status = #{httpStatus}, response_body = #{responseBody}, duration_millis = #{durationMillis}
            WHERE delivery_id = #{deliveryId} AND http_status IS NULL
            """)
    int complete(CallbackDeliveryResponseCommand command);
}
