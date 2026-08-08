package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionChannelInteractionLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelInteractionLogMapper
 * @date : 2026-07-14 19:42
 * @email : scott_x@163.com
 * @description : 渠道交互日志 Mapper，位于 service-payment 数据访问层，仅通过 transaction_channel_interaction_log 逻辑表读写。
 * @status : create
 */
public interface TransactionChannelInteractionLogMapper extends BaseMapper<TransactionChannelInteractionLogDO> {

    /**
     * 写入渠道交互日志逻辑表。
     *
     * @param logDO 渠道交互日志
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_channel_interaction_log
            (
              interaction_log_id, request_id, transaction_id, operation_id, channel_code,
              interaction_type, http_method, request_url_masked, http_status, request_header_json_masked,
              request_body_json_masked, response_header_json_masked, response_body_json_masked,
              exception_type, exception_message, duration_millis, trace_id, interaction_time,
              transaction_date_time, transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{logDO.interactionLogId}, #{logDO.requestId}, #{logDO.transactionId},
              #{logDO.operationId}, #{logDO.channelCode}, #{logDO.interactionType},
              #{logDO.httpMethod}, #{logDO.requestUrlMasked}, #{logDO.httpStatus},
              #{logDO.requestHeaderJsonMasked}, #{logDO.requestBodyJsonMasked},
              #{logDO.responseHeaderJsonMasked}, #{logDO.responseBodyJsonMasked},
              #{logDO.exceptionType}, #{logDO.exceptionMessage}, #{logDO.durationMillis},
              #{logDO.traceId}, #{logDO.interactionTime}, #{logDO.transactionDateTime},
              #{logDO.transactionUtcTime}, #{logDO.transactionTimeZone}, #{logDO.createTime}
            )
            """)
    int insertLogical(@Param("logDO") TransactionChannelInteractionLogDO logDO);

    /**
     * 在精确交易分片内读取渠道请求对应的唯一交互事实。
     *
     * @param requestId 平台渠道请求 ID
     * @param transactionDateTime 交易分片时间
     * @return 渠道交互事实，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_channel_interaction_log
            WHERE request_id = #{requestId}
              AND transaction_date_time = #{transactionDateTime}
            ORDER BY id DESC
            LIMIT 1
            """)
    TransactionChannelInteractionLogDO selectByRequestId(
            @Param("requestId") String requestId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 在精确交易分片内一次性回写渠道 HTTP 交互结果。
     * 本地准备阶段先写 REQUEST 事实，结果阶段只能填充尚无响应、异常和耗时的初始行。
     *
     * @param requestId 平台渠道请求 ID
     * @param transactionDateTime 交易分片时间
     * @param interactionType 渠道交互类型
     * @param httpMethod 渠道真实 HTTP 方法
     * @param requestUrlMasked 脱敏后的渠道请求 URL
     * @param httpStatus 渠道 HTTP 状态码
     * @param requestHeaderJsonMasked 脱敏请求头
     * @param requestBodyJsonMasked 脱敏请求体
     * @param responseHeaderJsonMasked 脱敏响应头
     * @param responseBodyJsonMasked 脱敏响应体
     * @param exceptionType 异常类型
     * @param exceptionMessage 异常摘要
     * @param durationMillis 渠道调用耗时
     * @param interactionTime 渠道响应或异常时间
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_channel_interaction_log
            SET interaction_type = #{interactionType},
                http_method = #{httpMethod},
                request_url_masked = #{requestUrlMasked},
                http_status = #{httpStatus},
                request_header_json_masked = #{requestHeaderJsonMasked},
                request_body_json_masked = #{requestBodyJsonMasked},
                response_header_json_masked = #{responseHeaderJsonMasked},
                response_body_json_masked = #{responseBodyJsonMasked},
                exception_type = #{exceptionType},
                exception_message = #{exceptionMessage},
                duration_millis = #{durationMillis},
                interaction_time = #{interactionTime}
            WHERE request_id = #{requestId}
              AND transaction_date_time = #{transactionDateTime}
              AND interaction_type = 'REQUEST'
              AND http_status IS NULL
              AND response_header_json_masked IS NULL
              AND response_body_json_masked IS NULL
              AND exception_type IS NULL
              AND exception_message IS NULL
              AND duration_millis IS NULL
            """)
    int updateByRequestIdLogical(@Param("requestId") String requestId,
                                 @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                 @Param("interactionType") String interactionType,
                                 @Param("httpMethod") String httpMethod,
                                 @Param("requestUrlMasked") String requestUrlMasked,
                                 @Param("httpStatus") Integer httpStatus,
                                 @Param("requestHeaderJsonMasked") String requestHeaderJsonMasked,
                                 @Param("requestBodyJsonMasked") String requestBodyJsonMasked,
                                 @Param("responseHeaderJsonMasked") String responseHeaderJsonMasked,
                                 @Param("responseBodyJsonMasked") String responseBodyJsonMasked,
                                 @Param("exceptionType") String exceptionType,
                                 @Param("exceptionMessage") String exceptionMessage,
                                 @Param("durationMillis") Integer durationMillis,
                                 @Param("interactionTime") LocalDateTime interactionTime);

}
