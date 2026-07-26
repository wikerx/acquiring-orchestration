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
 * @description : 交易渠道请求 Mapper，位于 service-payment 数据访问层，仅负责 transaction_channel_request 逻辑表及物理分表写入。
 * @status : create
 */
public interface TransactionChannelRequestMapper extends BaseMapper<TransactionChannelRequestDO> {

    /**
     * 写入渠道请求物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param requestDO         渠道请求记录
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
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
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       /**
                        * 完成 m 分支的校验或状态更新。
                        * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                        * <p>
                        * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                        * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                        * </p>
                        * @param requestDO request DO 输入值，含义由调用方法名称和所属业务对象限定
                        */
                       @Param("requestDO") TransactionChannelRequestDO requestDO);

    /**
     * 按 request_id 查询单笔渠道请求。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param requestId 平台渠道请求 ID
     * @return 渠道请求记录，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE request_id = #{requestId}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionChannelRequestDO selectByRequestIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                          /**
                                                           * 完成 m 分支的校验或状态更新。
                                                           * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                           * <p>
                                                           * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                           * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                           * </p>
                                                           * @param requestId request Id 输入值，含义由调用方法名称和所属业务对象限定
                                                           */
                                                          @Param("requestId") String requestId);

    /**
     * 按渠道恢复身份查询单笔渠道请求。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param channelCode 渠道编码
     * @param channelOrderNo 渠道订单号
     * @param channelTransactionId 渠道交易 ID
     * @return 渠道请求记录，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE channel_code = #{channelCode}
              AND channel_order_no = #{channelOrderNo}
              AND channel_transaction_id = #{channelTransactionId}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionChannelRequestDO selectByChannelTransactionPhysical(@Param("physicalTableName") String physicalTableName,
                                                                   @Param("channelCode") String channelCode,
                                                                   @Param("channelOrderNo") String channelOrderNo,
                                                                   /**
                                                                    * 完成 m 分支的校验或状态更新。
                                                                    * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                    * <p>
                                                                    * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                    * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                    * </p>
                                                                    * @param channelTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
                                                                    */
                                                                   @Param("channelTransactionId") String channelTransactionId);

    /**
     * CAS 推进渠道请求状态。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param requestId 平台渠道请求 ID
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
     * @return 影响行数，1 表示状态推进成功
     */
    @Update("""
            <script>
            UPDATE ${physicalTableName}
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
              AND version = #{expectedVersion}
              AND request_status IN
              <foreach collection="expectedStatuses" item="expectedStatus" open="(" separator="," close=")">
                #{expectedStatus}
              </foreach>
              AND deleted = 0
            </script>
            """)
    int updateStatusPhysical(@Param("physicalTableName") String physicalTableName,
                             @Param("requestId") String requestId,
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
                             /**
                              * 完成 m 分支的校验或状态更新。
                              * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                              * <p>
                              * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                              * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                              * </p>
                              * @param durationMillis duration Millis 输入值，含义由调用方法名称和所属业务对象限定
                              */
                             @Param("durationMillis") Integer durationMillis);

    /**
     * 按平台交易 ID 查询渠道请求摘要。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @return 渠道请求列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
              AND deleted = 0
            ORDER BY request_start_time DESC
            LIMIT 100
            """)
    List<TransactionChannelRequestDO> selectByTransactionIdPhysical(@Param("physicalTableName") String physicalTableName,
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
     * 按平台交易 ID 和渠道编码查询原资金动作渠道请求。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @param channelCode 渠道编码
     * @return 原资金动作渠道请求，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
              AND channel_code = #{channelCode}
              AND channel_match_flag = 0
              AND deleted = 0
            ORDER BY request_start_time ASC, id ASC
            LIMIT 1
            """)
    TransactionChannelRequestDO selectOriginalByTransactionPhysical(@Param("physicalTableName") String physicalTableName,
                                                                    @Param("transactionId") String transactionId,
                                                                    /**
                                                                     * 完成 m 分支的校验或状态更新。
                                                                     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                     * <p>
                                                                     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                     * </p>
                                                                     * @param channelCode channel Code 输入值，含义由调用方法名称和所属业务对象限定
                                                                     */
                                                                    @Param("channelCode") String channelCode);

    /**
     * 按 operation_id 查询同一生命周期的渠道请求摘要。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 渠道请求列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
              AND deleted = 0
            ORDER BY request_start_time DESC
            LIMIT 200
            """)
    List<TransactionChannelRequestDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
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
     * 按 request_id 批量查询渠道请求摘要，用于渠道交互日志分页后补齐业务结果。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param requestIds        渠道请求 ID 列表
     * @return 渠道请求摘要列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND request_id IN
              <foreach collection="requestIds" item="requestId" open="(" separator="," close=")">
                #{requestId}
              </foreach>
            </script>
            """)
    List<TransactionChannelRequestDO> selectByRequestIdsPhysical(@Param("physicalTableName") String physicalTableName,
                                                                 /**
                                                                  * 完成 m 分支的校验或状态更新。
                                                                  * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                  * <p>
                                                                  * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                  * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                  * </p>
                                                                  * @param requestIds request Ids 输入值，含义由调用方法名称和所属业务对象限定
                                                                  */
                                                                 @Param("requestIds") List<String> requestIds);

    /**
     * 按交易时间范围查询渠道请求摘要。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param channelCode 渠道编码，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param channelOrderNo 渠道订单号，可为空
     * @param requestStatus 请求状态，可为空
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @param offset 分页偏移
     * @param limit 分页大小
     * @return 渠道请求列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="channelCode != null and channelCode != ''">
                AND channel_code = #{channelCode}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
              <if test="channelOrderNo != null and channelOrderNo != ''">
                AND channel_order_no = #{channelOrderNo}
              </if>
              <if test="requestStatus != null and requestStatus != ''">
                AND request_status = #{requestStatus}
              </if>
            ORDER BY request_start_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TransactionChannelRequestDO> selectPagePhysical(@Param("physicalTableName") String physicalTableName,
                                                         @Param("channelCode") String channelCode,
                                                         @Param("transactionId") String transactionId,
                                                         @Param("channelOrderNo") String channelOrderNo,
                                                         @Param("requestStatus") String requestStatus,
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
     * 统计交易时间范围内的渠道请求摘要数量。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param channelCode 渠道编码，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param channelOrderNo 渠道订单号，可为空
     * @param requestStatus 请求状态，可为空
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @return 命中记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="channelCode != null and channelCode != ''">
                AND channel_code = #{channelCode}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
              <if test="channelOrderNo != null and channelOrderNo != ''">
                AND channel_order_no = #{channelOrderNo}
              </if>
              <if test="requestStatus != null and requestStatus != ''">
                AND request_status = #{requestStatus}
              </if>
            </script>
            """)
    long countPagePhysical(@Param("physicalTableName") String physicalTableName,
                           @Param("channelCode") String channelCode,
                           @Param("transactionId") String transactionId,
                           @Param("channelOrderNo") String channelOrderNo,
                           @Param("requestStatus") String requestStatus,
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
