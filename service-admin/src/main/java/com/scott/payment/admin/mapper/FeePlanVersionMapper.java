package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanVersionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 费用方案版本数据访问，审核时锁定目标版本。
 */
public interface FeePlanVersionMapper extends BaseMapper<FeePlanVersionDO> {

    /**
     * 锁定审核目标，避免重复审核覆盖状态。
     */
    @Select("SELECT * FROM fee_plan_version WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    FeePlanVersionDO selectByIdForUpdate(@Param("id") Long id);
}
