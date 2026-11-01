package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;

/**
 * 费用规则数据访问，不承载匹配或计费业务判断。
 */
public interface FeeRuleMapper extends BaseMapper<FeeRuleDO> {
}
