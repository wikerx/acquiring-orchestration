package com.scott.payment.admin.mapper;

import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.CurrencyPair;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeBusinessRateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 汇率波动趋势只读数据访问接口。 */
public interface ExchangeRateVolatilityMapper {

    /**
     * 按固定汇率类型、币种方向和时间范围读取全部来源的业务汇率事实。
     */
    @Select("""
            <script>
            SELECT id, rate_type, source_code, base_currency, quote_currency,
                   raw_rate_id, rule_id, original_rate, final_rate,
                   effective_time, expire_time, rate_status, create_time
            FROM exchange_business_rate
            WHERE deleted = 0
              AND rate_status IN ('ENABLED', 'EXPIRED')
              AND rate_type = #{rateType}
              AND effective_time &gt;= #{startTime}
              AND effective_time &lt;= #{endTime}
              <if test="pairs != null and pairs.size() > 0">
                AND (
                  <foreach collection="pairs" item="pair" separator=" OR ">
                    (base_currency = #{pair.baseCurrency} AND quote_currency = #{pair.quoteCurrency})
                  </foreach>
                )
              </if>
            ORDER BY base_currency, quote_currency, effective_time, create_time, id
            </script>
            """)
    List<ExchangeBusinessRateDO> selectTrendFacts(@Param("rateType") String rateType,
                                                   @Param("pairs") List<CurrencyPair> pairs,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);
}
