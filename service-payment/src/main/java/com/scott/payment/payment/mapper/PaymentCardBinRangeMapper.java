package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCardBinRangeMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 支付服务卡 BIN 只读查询 Mapper。
 * @status : create
 */
public interface PaymentCardBinRangeMapper extends BaseMapper<PaymentCardBinRangeDO> {

    /**
     * 查询当前时刻生效且覆盖卡 BIN 数值的最精确区间。
     *
     * <p>候选起始值由调用方按不同 BIN 长度预先生成；查询按 BIN 长度降序，
     * 再按更新时间和主键降序选择唯一结果，确保更长、更具体的区间优先。</p>
     *
     * @param candidateStarts 不同 BIN 长度对应的数值化起始候选集合
     * @param numericValue 卡号前缀的数值化结果，用于校验区间结束边界
     * @return 最佳有效 BIN 区间；未命中时返回 null
     */
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
