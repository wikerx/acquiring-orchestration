package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionChannelCallbackLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackLogMapper
 * @date : 2026-07-14 22:24
 * @email : scott_x@163.com
 * @description : 渠道回调原始日志 Mapper，位于 service-payment 数据访问层，仅访问 transaction_channel_callback_log 逻辑表。
 * @status : create
 */
public interface TransactionChannelCallbackLogMapper extends BaseMapper<TransactionChannelCallbackLogDO> {

    /**
     * 写入渠道回调原始日志逻辑表。
     *
     * @param logDO 渠道回调原始日志
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_channel_callback_log
            (
              callback_log_id, transaction_id, operation_id, channel_code, callback_type,
              channel_order_no, channel_transaction_id, request_uri, http_method, source_ip,
              request_header_json_masked, request_body_json_masked, signature_valid, ip_allowed,
              platform_response_code, platform_response_body, callback_received_time,
              transaction_date_time, transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{logDO.callbackLogId}, #{logDO.transactionId}, #{logDO.operationId},
              #{logDO.channelCode}, #{logDO.callbackType}, #{logDO.channelOrderNo},
              #{logDO.channelTransactionId}, #{logDO.requestUri}, #{logDO.httpMethod},
              #{logDO.sourceIp}, #{logDO.requestHeaderJsonMasked}, #{logDO.requestBodyJsonMasked},
              #{logDO.signatureValid}, #{logDO.ipAllowed}, #{logDO.platformResponseCode},
              #{logDO.platformResponseBody}, #{logDO.callbackReceivedTime},
              #{logDO.transactionDateTime}, #{logDO.transactionUtcTime}, #{logDO.transactionTimeZone},
              #{logDO.createTime}
            )
            """)
    int insertLogical(@Param("logDO") TransactionChannelCallbackLogDO logDO);

}
