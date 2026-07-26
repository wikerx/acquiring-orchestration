package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateRuleDO;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeJobRateRuleMapper
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : ExchangeJobRateRuleMapper MyBatis 数据访问接口，用于映射数据库表读写语句和领域查询条件，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface ExchangeJobRateRuleMapper extends BaseMapper<ExchangeRateRuleDO> {
}
