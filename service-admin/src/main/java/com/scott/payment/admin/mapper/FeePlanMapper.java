package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeePlanMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 费用方案数据访问，锁定主记录以串行化版本号分配。
 * @status : create
 */
public interface FeePlanMapper extends BaseMapper<FeePlanDO> {

    /** 按主键锁定未归档删除的方案。 */
    @Select("SELECT * FROM fee_plan WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    FeePlanDO selectByIdForUpdate(@Param("id") Long id);

    /** 按商户号锁定商户费用方案。 */
    @Select("SELECT * FROM fee_plan WHERE plan_type = 'MERCHANT' AND merchant_id = #{merchantId} AND deleted = 0 FOR UPDATE")
    FeePlanDO selectMerchantPlanForUpdate(@Param("merchantId") String merchantId);
}
