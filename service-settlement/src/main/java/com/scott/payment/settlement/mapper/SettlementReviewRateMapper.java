package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewRateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewRateMapper
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 预审统一汇率矩阵只追加数据访问接口；审批阶段必须读取冻结直接汇率，不重新向报价源取价。
 * @status : create
 */
public interface SettlementReviewRateMapper {

    /**
     * 批量追加预审不可变汇率矩阵，来源币种唯一冲突保持原行不变。
     * @param rows 已归一并冻结来源信息的直接汇率行
     * @return 实际插入行数，调用方必须与集合大小核对
     */
    @Insert("""
            <script>
            INSERT INTO settlement_review_rate
            (review_order_no, source_currency, target_currency, direct_rate,
             source_currency_exponent, target_currency_exponent, rate_source, quote_id,
             source_quote_direction, effective_time, locked_time, locked_by, create_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.reviewOrderNo}, #{row.sourceCurrency}, #{row.targetCurrency}, #{row.directRate},
                 #{row.sourceCurrencyExponent}, #{row.targetCurrencyExponent}, #{row.rateSource},
                 #{row.quoteId}, #{row.sourceQuoteDirection}, #{row.effectiveTime}, #{row.lockedTime},
                 #{row.lockedBy}, #{row.createTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertBatchIdempotent(@Param("rows") List<SettlementReviewRateDO> rows);

    /**
     * 按来源币种稳定顺序读取预审冻结汇率矩阵。
     * @param reviewOrderNo 结算预审单号
     * @return 冻结汇率行集合
     */
    @Select("""
            SELECT *
            FROM settlement_review_rate
            WHERE review_order_no = #{reviewOrderNo}
            ORDER BY source_currency ASC, id ASC
            """)
    List<SettlementReviewRateDO> selectByOrderNo(@Param("reviewOrderNo") String reviewOrderNo);
}
