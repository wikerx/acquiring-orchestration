package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateRuleDO;


/**
 * Exchange Job Rate Rule Mapper 映射组件，位于 调度任务服务，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。
 */
public interface ExchangeJobRateRuleMapper extends BaseMapper<ExchangeRateRuleDO> {
}
