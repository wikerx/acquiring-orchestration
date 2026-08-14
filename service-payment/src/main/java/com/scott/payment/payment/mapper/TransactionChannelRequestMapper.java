package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelRequestMapper
 * @date : 2026-07-14 19:40
 * @email : scott_x@163.com
 * @description : 交易渠道请求 Mapper，位于 service-payment 数据访问层，仅访问 transaction_channel_request 逻辑表，季度路由由 ShardingSphere 负责。
 * @status : create
 */
public interface TransactionChannelRequestMapper extends BaseMapper<TransactionChannelRequestDO> {

    /**
     * 写入渠道请求逻辑表。
     *
     * @param requestDO 渠道请求记录
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_channel_request
            (
              request_id, transaction_id, operation_id, channel_id, channel_code, channel_mid_config_id,
              transaction_type, request_scene, channel_match_flag, request_status, http_method,
              request_url_masked, request_currency, request_amount, channel_order_no, channel_transaction_id,
              gateway_result, gateway_code, acquirer_code, acquirer_message, channel_status,
              platform_success, platform_result_code, platform_fail_reason, request_start_time,
              response_time, duration_millis, transaction_date_time, transaction_utc_time,
              transaction_time_zone, version, deleted, create_time, update_time
            )
            VALUES
            (
              #{requestDO.requestId}, #{requestDO.transactionId}, #{requestDO.operationId},
              #{requestDO.channelId}, #{requestDO.channelCode}, #{requestDO.channelMidConfigId},
              #{requestDO.transactionType}, #{requestDO.requestScene}, #{requestDO.channelMatchFlag},
              #{requestDO.requestStatus}, #{requestDO.httpMethod}, #{requestDO.requestUrlMasked},
              #{requestDO.requestCurrency}, #{requestDO.requestAmount}, #{requestDO.channelOrderNo},
              #{requestDO.channelTransactionId}, #{requestDO.gatewayResult}, #{requestDO.gatewayCode},
              #{requestDO.acquirerCode}, #{requestDO.acquirerMessage}, #{requestDO.channelStatus},
              #{requestDO.platformSuccess}, #{requestDO.platformResultCode}, #{requestDO.platformFailReason},
              #{requestDO.requestStartTime}, #{requestDO.responseTime}, #{requestDO.durationMillis},
              #{requestDO.transactionDateTime}, #{requestDO.transactionUtcTime}, #{requestDO.transactionTimeZone},
              #{requestDO.version}, #{requestDO.deleted}, #{requestDO.createTime}, #{requestDO.updateTime}
            )
            """)
    int insertLogical(@Param("requestDO") TransactionChannelRequestDO requestDO);

    /**
     * 按请求 ID 和精确分片时间查询渠道请求。
     *
     * @param requestId 平台渠道请求 ID
     * @param transactionDateTime 交易分片时间
     * @return 渠道请求记录，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_channel_request
            WHERE request_id = #{requestId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionChannelRequestDO selectByRequestId(@Param("requestId") String requestId,
                                                  @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 外部资金请求发起前抢占 INIT 记录，保证并发和重放只有一个调用方可以访问 PSP。
     */
    @Update("""
            UPDATE transaction_channel_request
            SET request_status = 'SENT',
                version = version + 1,
                update_time = #{now}
            WHERE request_id = #{requestId}
              AND transaction_date_time = #{transactionDateTime}
              AND request_status = 'INIT'
              AND deleted = 0
            """)
    int claimSubmissionLogical(@Param("requestId") String requestId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("now") LocalDateTime now);

    /** 认证前失败只允许从 INIT 抢占，不能覆盖可能已到达 PSP 的 SENT 请求。 */
    @Update("""
            UPDATE transaction_channel_request
            SET request_status = 'FAILED',
                version = version + 1,
                update_time = #{now}
            WHERE request_id = #{requestId}
              AND transaction_date_time = #{transactionDateTime}
              AND request_status = 'INIT'
              AND deleted = 0
            """)
    int claimPreChannelFailureLogical(@Param("requestId") String requestId,
                                      @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                      @Param("now") LocalDateTime now);

    /**
     * 在受控半开时间范围内按渠道身份恢复请求。
     *
     * @param channelCode 渠道编码
     * @param channelOrderNo 渠道订单号
     * @param channelTransactionId 渠道交易 ID
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 渠道请求记录，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_channel_request
            WHERE channel_code = #{channelCode}
              AND channel_order_no = #{channelOrderNo}
              AND channel_transaction_id = #{channelTransactionId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND deleted = 0
            ORDER BY transaction_date_time DESC, id DESC
            LIMIT 1
            """)
    TransactionChannelRequestDO selectByChannelTransaction(
            @Param("channelCode") String channelCode,
            @Param("channelOrderNo") String channelOrderNo,
            @Param("channelTransactionId") String channelTransactionId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 使用分片时间、版本和允许状态 CAS 推进渠道请求。
     *
     * @param requestId 平台渠道请求 ID
     * @param transactionDateTime 交易分片时间
     * @param expectedVersion 读取渠道请求时的版本号
     * @param expectedStatuses 允许推进的当前请求状态
     * @param requestStatus 目标请求状态
     * @param gatewayResult 渠道网关外层结果
     * @param gatewayCode 渠道网关响应码
     * @param acquirerCode 收单响应码
     * @param acquirerMessage 收单响应描述
     * @param channelStatus 渠道原始状态
     * @param platformSuccess 平台成功判断
     * @param platformResultCode 平台统一结果码
     * @param platformFailReason 平台失败原因
     * @param responseTime 响应时间
     * @param durationMillis 请求耗时
     * @return 影响行数
     */
    @Update("""
            <script>
            UPDATE transaction_channel_request
            SET request_status = #{requestStatus},
                gateway_result = #{gatewayResult},
                gateway_code = #{gatewayCode},
                acquirer_code = #{acquirerCode},
                acquirer_message = #{acquirerMessage},
                channel_status = #{channelStatus},
                platform_success = #{platformSuccess},
                platform_result_code = #{platformResultCode},
                platform_fail_reason = #{platformFailReason},
                response_time = #{responseTime},
                duration_millis = #{durationMillis},
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE request_id = #{requestId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND request_status IN
              <foreach collection="expectedStatuses" item="expectedStatus" open="(" separator="," close=")">
                #{expectedStatus}
              </foreach>
              AND deleted = 0
            </script>
            """)
    int updateStatusLogical(@Param("requestId") String requestId,
                            @Param("transactionDateTime") LocalDateTime transactionDateTime,
                            @Param("expectedVersion") Integer expectedVersion,
                            @Param("expectedStatuses") List<String> expectedStatuses,
                            @Param("requestStatus") String requestStatus,
                            @Param("gatewayResult") String gatewayResult,
                            @Param("gatewayCode") String gatewayCode,
                            @Param("acquirerCode") String acquirerCode,
                            @Param("acquirerMessage") String acquirerMessage,
                            @Param("channelStatus") String channelStatus,
                            @Param("platformSuccess") Integer platformSuccess,
                            @Param("platformResultCode") String platformResultCode,
                            @Param("platformFailReason") String platformFailReason,
                            @Param("responseTime") LocalDateTime responseTime,
                            @Param("durationMillis") Integer durationMillis);

    /**
     * 按交易 ID、渠道和精确分片时间查询原资金动作请求。
     *
     * @param transactionId 平台当前交易 ID
     * @param channelCode 渠道编码
     * @param transactionDateTime 交易分片时间
     * @return 原资金动作渠道请求，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_channel_request
            WHERE transaction_id = #{transactionId}
              AND channel_code = #{channelCode}
              AND transaction_date_time = #{transactionDateTime}
              AND channel_match_flag = 0
              AND deleted = 0
            ORDER BY request_start_time ASC, id ASC
            LIMIT 1
            """)
    TransactionChannelRequestDO selectOriginalByTransaction(
            @Param("transactionId") String transactionId,
            @Param("channelCode") String channelCode,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

}
