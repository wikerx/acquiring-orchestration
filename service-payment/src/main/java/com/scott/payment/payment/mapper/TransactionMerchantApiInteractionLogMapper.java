package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionMerchantApiInteractionLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantApiInteractionLogMapper
 * @date : 2026-07-15 23:22
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 交互日志 Mapper，位于 service-payment 数据访问层，仅负责 transaction_merchant_api_interaction_log 物理分表写入和详情聚合查询。
 * @status : create
 */
public interface TransactionMerchantApiInteractionLogMapper extends BaseMapper<TransactionMerchantApiInteractionLogDO> {

    /**
     * 写入商户 OpenAPI 交互日志物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param logDO             商户 API 交互日志
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
            (
              api_log_id, request_id, transaction_id, operation_id, merchant_id,
              merchant_order_no, merchant_order_id, api_operation, request_path, request_time,
              request_result, request_cipher_digest, request_cipher_masked, request_plain_json_masked,
              response_time, response_result, merchant_response_code, merchant_response_message,
              response_plain_json_masked, response_cipher_digest, response_cipher_masked,
              duration_millis, trace_id, transaction_date_time, transaction_utc_time,
              transaction_time_zone, create_time
            )
            VALUES
            (
              #{logDO.apiLogId}, #{logDO.requestId}, #{logDO.transactionId}, #{logDO.operationId},
              #{logDO.merchantId}, #{logDO.merchantOrderNo}, #{logDO.merchantOrderId},
              #{logDO.apiOperation}, #{logDO.requestPath}, #{logDO.requestTime},
              #{logDO.requestResult}, #{logDO.requestCipherDigest}, #{logDO.requestCipherMasked},
              #{logDO.requestPlainJsonMasked}, #{logDO.responseTime}, #{logDO.responseResult},
              #{logDO.merchantResponseCode}, #{logDO.merchantResponseMessage},
              #{logDO.responsePlainJsonMasked}, #{logDO.responseCipherDigest},
              #{logDO.responseCipherMasked}, #{logDO.durationMillis}, #{logDO.traceId},
              #{logDO.transactionDateTime}, #{logDO.transactionUtcTime}, #{logDO.transactionTimeZone},
              #{logDO.createTime}
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
                       @Param("logDO") TransactionMerchantApiInteractionLogDO logDO);

    /**
     * 按 operation_id 查询同一生命周期的商户 API 交互日志。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 商户 API 交互日志列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
            ORDER BY request_time DESC, id DESC
            LIMIT 500
            """)
    List<TransactionMerchantApiInteractionLogDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
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
     * 回写商户 OpenAPI 响应加密后的密文摘要。
     *
     * @param physicalTableName       经分表规则解析器校验后的物理表名
     * @param transactionId           平台当前交易 ID
     * @param requestId               商户请求唯一号，可为空
     * @param responsePlainJsonMasked 平台响应脱敏明文，可为空
     * @param responseCipherDigest    平台响应密文摘要
     * @param responseCipherMasked    平台响应密文掩码
     * @param responseTime            响应加密完成时间
     * @return 影响行数
     */
    @Update("""
            <script>
            UPDATE ${physicalTableName}
            SET response_cipher_digest = #{responseCipherDigest},
                response_cipher_masked = #{responseCipherMasked},
                response_time = #{responseTime}
                <if test="responsePlainJsonMasked != null and responsePlainJsonMasked != ''">
                  , response_plain_json_masked = #{responsePlainJsonMasked}
                </if>
            WHERE transaction_id = #{transactionId}
              <if test="requestId != null and requestId != ''">
                AND request_id = #{requestId}
              </if>
            </script>
            """)
    int updateResponseCipherPhysical(@Param("physicalTableName") String physicalTableName,
                                     @Param("transactionId") String transactionId,
                                     @Param("requestId") String requestId,
                                     @Param("responsePlainJsonMasked") String responsePlainJsonMasked,
                                     @Param("responseCipherDigest") String responseCipherDigest,
                                     @Param("responseCipherMasked") String responseCipherMasked,
                                     /**
                                      * 完成 m 分支的校验或状态更新。
                                      * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                      * <p>
                                      * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                      * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                      * </p>
                                      * @param responseTime 时间值，使用系统约定时区或调用方传入的业务时区解释
                                      */
                                     @Param("responseTime") LocalDateTime responseTime);

    /**
     * 回写商户 OpenAPI 同步响应最终业务结果。
     * <p>
     * 首次交易在渠道调用前会先记录 PROCESSING 受理日志；同步渠道返回后需要把日志更新成实际返回商户的终态响应。
     *
     * @param physicalTableName       经分表规则解析器校验后的物理表名
     * @param transactionId           平台当前交易 ID
     * @param requestId               商户请求唯一号，可为空
     * @param requestResult           最终请求处理结果
     * @param responseResult          最终响应处理结果
     * @param merchantResponseCode    商户侧可见响应码
     * @param merchantResponseMessage 商户侧可见响应描述
     * @param responsePlainJsonMasked 平台最终响应脱敏明文
     * @param responseTime            响应完成时间
     * @param durationMillis          OpenAPI 到当前响应耗时
     * @return 影响行数
     */
    @Update("""
            <script>
            UPDATE ${physicalTableName}
            SET request_result = #{requestResult},
                response_result = #{responseResult},
                merchant_response_code = #{merchantResponseCode},
                merchant_response_message = #{merchantResponseMessage},
                response_plain_json_masked = #{responsePlainJsonMasked},
                response_time = #{responseTime},
                duration_millis = #{durationMillis}
            WHERE transaction_id = #{transactionId}
              <if test="requestId != null and requestId != ''">
                AND request_id = #{requestId}
              </if>
            </script>
            """)
    int updateFinalResultPhysical(@Param("physicalTableName") String physicalTableName,
                                  @Param("transactionId") String transactionId,
                                  @Param("requestId") String requestId,
                                  @Param("requestResult") String requestResult,
                                  @Param("responseResult") String responseResult,
                                  @Param("merchantResponseCode") String merchantResponseCode,
                                  @Param("merchantResponseMessage") String merchantResponseMessage,
                                  @Param("responsePlainJsonMasked") String responsePlainJsonMasked,
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
}
