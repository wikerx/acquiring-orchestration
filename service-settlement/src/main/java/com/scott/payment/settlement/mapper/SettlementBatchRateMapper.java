package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementBatchRateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchRateMapper
 * @date : 2026-08-26 23:20
 * @email : scott_x@163.com
 * @description : 批次汇率只追加和读取 Mapper；不暴露 UPDATE 或 DELETE，幂等冲突必须回读完整身份。
 * @status : create
 */
public interface SettlementBatchRateMapper {

    /** 一次追加完整矩阵；唯一键冲突只保留原行，调用方随后逐项回读验证。 */
    @Insert("""
            <script>
            INSERT INTO settlement_batch_rate
            (settlement_batch_no, source_currency, target_currency, rate_type, direct_rate,
             source_currency_exponent, target_currency_exponent, rate_source, quote_id,
             source_quote_direction, effective_time, locked_time, locked_by, rate_status, create_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.settlementBatchNo}, #{row.sourceCurrency}, #{row.targetCurrency}, #{row.rateType},
                 #{row.directRate}, #{row.sourceCurrencyExponent}, #{row.targetCurrencyExponent},
                 #{row.rateSource}, #{row.quoteId}, #{row.sourceQuoteDirection}, #{row.effectiveTime},
                 #{row.lockedTime}, #{row.lockedBy}, #{row.rateStatus}, #{row.createTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertBatchIdempotent(@Param("rows") List<SettlementBatchRateDO> rows);

    /** 按原币种稳定顺序读取批次完整矩阵。 */
    @Select("""
            SELECT *
            FROM settlement_batch_rate
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND rate_type = 'SETTLEMENT'
              AND rate_status = 'LOCKED'
            ORDER BY source_currency ASC, id ASC
            """)
    List<SettlementBatchRateDO> selectByBatchNo(
            @Param("settlementBatchNo") String settlementBatchNo);
}
