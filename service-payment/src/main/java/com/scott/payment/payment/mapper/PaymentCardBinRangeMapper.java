package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.time.LocalDateTime;

/** 支付服务卡 BIN 只读查询 Mapper。 */
public interface PaymentCardBinRangeMapper extends BaseMapper<PaymentCardBinRangeDO> {

    @Select("""
            <script>
            SELECT *
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND card_bin_start IN
              <foreach collection="candidateStarts" item="candidate" open="(" separator="," close=")">
                #{candidate}
              </foreach>
              AND card_bin_end >= #{numericValue}
              AND (effective_time IS NULL OR effective_time &lt;= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            ORDER BY bin_length DESC, update_time DESC, id DESC
            LIMIT 1
            </script>
            """)
    PaymentCardBinRangeDO selectBestMatch(@Param("candidateStarts") List<Long> candidateStarts,
                                          @Param("numericValue") long numericValue);

    /** 查询当前未命中前缀可能命中的最近未来生效时间。 */
    @Select("""
            <script>
            SELECT MIN(effective_time)
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status IN (1, 2)
              AND card_bin_start IN
              <foreach collection="candidateStarts" item="candidate" open="(" separator="," close=")">
                #{candidate}
              </foreach>
              AND card_bin_end >= #{numericValue}
              AND effective_time > CURRENT_TIMESTAMP(3)
              AND (expire_time IS NULL OR expire_time > effective_time)
            </script>
            """)
    LocalDateTime selectNextEffectiveTime(@Param("candidateStarts") List<Long> candidateStarts,
                                          @Param("numericValue") long numericValue);
}
