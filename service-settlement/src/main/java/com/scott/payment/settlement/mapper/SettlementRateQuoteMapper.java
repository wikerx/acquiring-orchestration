package com.scott.payment.settlement.mapper;

import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.settlement.entity.SettlementRateQuoteDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementRateQuoteMapper
 * @date : 2026-08-26 22:40
 * @email : scott_x@163.com
 * @description : 批量读取结算币种对直接和反向有效业务汇率及来源排序，禁止逐币种 N+1 查询和缺失汇率兜底。
 * @status : create
 */
public interface SettlementRateQuoteMapper {

    /** 每个标准币种对在同一 SQL 中同时查询直接和反向报价。 */
    @Select("""
            <script>
            SELECT rate.id,
                   rate.source_code,
                   rate.base_currency,
                   rate.quote_currency,
                   rate.final_rate,
                   rate.effective_time,
                   source.default_source,
                   source.priority AS source_priority
            FROM exchange_business_rate rate
            INNER JOIN exchange_rate_source source
                    ON source.source_code = rate.source_code
                   AND source.source_status = 1
                   AND source.deleted = 0
            WHERE rate.deleted = 0
              AND rate.rate_type = 'SETTLEMENT_RATE'
              AND rate.rate_status = 'ENABLED'
              AND rate.final_rate > 0
              AND rate.effective_time &lt;= #{valuationTime}
              AND (rate.expire_time IS NULL OR rate.expire_time &gt; #{valuationTime})
              AND (
                <foreach collection="pairs" item="pair" separator=" OR ">
                    ((rate.base_currency = #{pair.sourceCurrency}
                      AND rate.quote_currency = #{pair.targetCurrency})
                     OR
                     (rate.base_currency = #{pair.targetCurrency}
                      AND rate.quote_currency = #{pair.sourceCurrency}))
                </foreach>
              )
            </script>
            """)
    List<SettlementRateQuoteDO> selectEffectiveQuotes(
            @Param("pairs") List<CurrencyPair> pairs,
            @Param("valuationTime") LocalDateTime valuationTime);
}
