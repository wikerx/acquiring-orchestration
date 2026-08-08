package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 支付服务卡 BIN 只读查询 Mapper。 */
public interface PaymentCardBinRangeMapper extends BaseMapper<PaymentCardBinRangeDO> {

    @Select("""
            SELECT *
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND card_bin_start <= #{numericValue}
              AND card_bin_end >= #{numericValue}
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            ORDER BY bin_length DESC, source_priority DESC, update_time DESC, id DESC
            LIMIT 1
            """)
    PaymentCardBinRangeDO selectBestMatch(@Param("numericValue") long numericValue);
}
