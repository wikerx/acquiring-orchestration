package com.scott.payment.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.data.entity.DataExchangeRateUsageSnapshotDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 汇率使用快照数据访问接口。
 *
 * <p>只负责快照插入和按最终幂等键回读，不在 Mapper 中解释汇率或结算业务语义。</p>
 */
public interface DataExchangeRateUsageSnapshotMapper extends BaseMapper<DataExchangeRateUsageSnapshotDO> {

    /**
     * 按全局使用事件号读取未删除快照，用于唯一键冲突后的事实一致性校验。
     *
     * @param usageEventId 全局稳定汇率使用事件号
     * @return 已提交快照；不存在时返回空
     */
    @Select("""
            SELECT *
            FROM exchange_rate_usage_snapshot
            WHERE usage_event_id = #{usageEventId}
              AND deleted = 0
            LIMIT 1
            """)
    DataExchangeRateUsageSnapshotDO selectByUsageEventId(@Param("usageEventId") String usageEventId);
}
