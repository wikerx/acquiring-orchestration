package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionChannelInteractionLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
                       /**
                        * 完成 m 分支的校验或状态更新。
                        * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                        * <p>
                        * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                        * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                        * </p>
                        * @param logDO log DO 输入值，含义由调用方法名称和所属业务对象限定
                        */
                       @Param("logDO") TransactionChannelInteractionLogDO logDO);

    /**
     * 按平台渠道请求 ID 回写真实渠道 HTTP 交互日志。
     * <p>
     * 首次准备事务会先保留一条平台内部请求快照；渠道同步结果返回后必须用渠道适配器提供的脱敏原始 HTTP
     * 请求/响应覆盖该快照，确保管理后台详情看到 MPGS 等渠道的真实交互报文。
     *
     * @param physicalTableName  经分表规则解析器校验后的物理表名
     * @param requestId          平台渠道请求 ID
     * @param httpMethod         渠道真实 HTTP 方法
     * @param requestUrlMasked   脱敏后的渠道真实请求 URL
     * @param httpStatus         渠道 HTTP 状态码
     * @param requestHeaderJsonMasked 脱敏请求头
     * @param requestBodyJsonMasked   脱敏请求体
     * @param responseHeaderJsonMasked 脱敏响应头
     * @param responseBodyJsonMasked   脱敏响应体
     * @param exceptionType      异常类型
     * @param exceptionMessage   异常摘要
     * @param durationMillis     渠道调用耗时
     * @param interactionTime    渠道响应或异常时间
     * @return 影响行数
     */
    @Update("""
            UPDATE ${physicalTableName}
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
            """)
    int updateByRequestIdPhysical(@Param("physicalTableName") String physicalTableName,
                                  @Param("requestId") String requestId,
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
                                  /**
                                   * 完成 m 分支的校验或状态更新。
                                   * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                   * <p>
                                   * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                   * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                   * </p>
                                   * @param interactionTime 时间值，使用系统约定时区或调用方传入的业务时区解释
                                   */
                                  @Param("interactionTime") LocalDateTime interactionTime);

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
                                                                /**
                                                                 * 完成 m 分支的校验或状态更新。
                                                                 * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                 * <p>
                                                                 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                 * </p>
                                                                 * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
                                                                 */
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
                           /**
                            * 完成 m 分支的校验或状态更新。
                            * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                            * <p>
                            * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                            * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                            * </p>
                            * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
                            */
                           @Param("endTime") LocalDateTime endTime);
}
