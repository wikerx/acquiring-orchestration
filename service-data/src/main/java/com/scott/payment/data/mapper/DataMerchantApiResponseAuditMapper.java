package com.scott.payment.data.mapper;

import com.scott.payment.data.entity.DataMerchantApiResponseAuditDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商户 OpenAPI 响应审计 Mapper。
 *
 * <p>只通过交易号和真实交易时间访问分片逻辑表。写入条件禁止不同密文摘要覆盖已经
 * 提交的首次响应，更新未命中时由服务层回查事实并区分重复、冲突和基础记录未就绪。</p>
 */
public interface DataMerchantApiResponseAuditMapper {

    /**
     * 仅在响应摘要尚未写入时补齐首次商户响应审计。
     *
     * @param audit 已完成二次脱敏和长度收敛的响应审计
     * @return 影响行数；零行需要回查事实，大于一行属于数据异常
     */
    @Update("""
            <script>
            UPDATE transaction_merchant_api_interaction_log
            SET response_cipher_digest = #{audit.responseCipherDigest},
                response_cipher_masked = #{audit.responseCipherMasked},
                response_time = #{audit.responseTime}
                <if test="audit.responsePlainJsonMasked != null and audit.responsePlainJsonMasked != ''">
                  , response_plain_json_masked = #{audit.responsePlainJsonMasked}
                </if>
            WHERE transaction_id = #{audit.transactionId}
              AND transaction_date_time = #{audit.transactionDateTime}
              AND response_cipher_digest IS NULL
              <if test="audit.requestId != null and audit.requestId != ''">
                AND request_id = #{audit.requestId}
              </if>
            </script>
            """)
    int updateFirstResponseAudit(@Param("audit") DataMerchantApiResponseAuditDO audit);

    /**
     * 回查同一交易分片中的已提交响应事实。
     *
     * <p>最多返回两行，用于在请求号缺失的兼容消息中发现非唯一交易事实，禁止静默选择
     * 任意一行并误判幂等。</p>
     *
     * @param audit 只使用交易号、交易时间和可选请求号定位
     * @return 零至两条已提交响应事实
     */
    @Select("""
            <script>
            SELECT transaction_id, transaction_date_time, request_id,
                   response_plain_json_masked, response_cipher_digest,
                   response_cipher_masked, response_time
            FROM transaction_merchant_api_interaction_log
            WHERE transaction_id = #{audit.transactionId}
              AND transaction_date_time = #{audit.transactionDateTime}
              <if test="audit.requestId != null and audit.requestId != ''">
                AND request_id = #{audit.requestId}
              </if>
            ORDER BY id ASC
            LIMIT 2
            </script>
            """)
    List<DataMerchantApiResponseAuditDO> selectResponseFacts(@Param("audit") DataMerchantApiResponseAuditDO audit);
}
