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
                                     @Param("responseTime") LocalDateTime responseTime);
}
