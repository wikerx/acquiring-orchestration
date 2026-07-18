package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionChannelInteractionLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelInteractionLogMapper
 * @date : 2026-07-14 19:42
 * @email : scott_x@163.com
 * @description : 渠道交互日志 Mapper，位于 service-payment 数据访问层，仅负责 transaction_channel_interaction_log 物理分表写入。
 * @status : create
 */
public interface TransactionChannelInteractionLogMapper extends BaseMapper<TransactionChannelInteractionLogDO> {

    /**
     * 写入渠道交互日志物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param logDO             渠道交互日志
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
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
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       @Param("logDO") TransactionChannelInteractionLogDO logDO);

    /**
     * 按平台交易 ID 查询渠道交互日志。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @return 渠道交互日志列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
            ORDER BY interaction_time DESC
            LIMIT 200
            """)
    List<TransactionChannelInteractionLogDO> selectByTransactionIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                           @Param("transactionId") String transactionId);

    /**
     * 按 operation_id 查询同一生命周期的渠道交互日志。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 渠道交互日志列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
            ORDER BY interaction_time DESC
            LIMIT 500
            """)
    List<TransactionChannelInteractionLogDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                         @Param("operationId") String operationId);

    /**
     * 按交易时间范围查询渠道交互日志。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param channelCode 渠道编码，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param interactionType 交互类型，可为空
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @param offset 分页偏移
     * @param limit 分页大小
     * @return 渠道交互日志列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="channelCode != null and channelCode != ''">
                AND channel_code = #{channelCode}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
              <if test="interactionType != null and interactionType != ''">
                <choose>
                  <when test="interactionType == 'REQUEST' or interactionType == 'RESPONSE'">
                    AND interaction_type IN (#{interactionType}, 'REQUEST_RESPONSE')
                  </when>
                  <otherwise>
                    AND interaction_type = #{interactionType}
                  </otherwise>
                </choose>
              </if>
            ORDER BY interaction_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TransactionChannelInteractionLogDO> selectPagePhysical(@Param("physicalTableName") String physicalTableName,
                                                                @Param("channelCode") String channelCode,
                                                                @Param("transactionId") String transactionId,
                                                                @Param("interactionType") String interactionType,
                                                                @Param("beginTime") LocalDateTime beginTime,
                                                                @Param("endTime") LocalDateTime endTime,
                                                                @Param("offset") long offset,
                                                                @Param("limit") long limit);

    /**
     * 统计交易时间范围内的渠道交互日志数量。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param channelCode 渠道编码，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param interactionType 交互类型，可为空
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @return 命中记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${physicalTableName}
            WHERE transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="channelCode != null and channelCode != ''">
                AND channel_code = #{channelCode}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
              <if test="interactionType != null and interactionType != ''">
                <choose>
                  <when test="interactionType == 'REQUEST' or interactionType == 'RESPONSE'">
                    AND interaction_type IN (#{interactionType}, 'REQUEST_RESPONSE')
                  </when>
                  <otherwise>
                    AND interaction_type = #{interactionType}
                  </otherwise>
                </choose>
              </if>
            </script>
            """)
    long countPagePhysical(@Param("physicalTableName") String physicalTableName,
                           @Param("channelCode") String channelCode,
                           @Param("transactionId") String transactionId,
                           @Param("interactionType") String interactionType,
                           @Param("beginTime") LocalDateTime beginTime,
                           @Param("endTime") LocalDateTime endTime);
}
