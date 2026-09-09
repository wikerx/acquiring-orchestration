package com.scott.payment.clearing.service;

import com.scott.payment.clearing.domain.model.ClearingCalculationModels.ClearingCalculationCommand;
import com.scott.payment.clearing.domain.model.ClearingCalculationModels.ClearingCalculationResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCalculationService
 * @date : 2026-08-26 09:32
 * @email : scott_x@163.com
 * @description : 费用与保证金纯计算编排边界，不访问数据库、Redis、RocketMQ、汇率或余额。
 * @status : create
 */
public interface ClearingCalculationService {

    /**
     * 按动作终态和已冻结商户配置生成原币种清分事实。
     *
     * @param command 权威动作、费用版本、规则匹配维度和累计事实
     * @return 分离的本金、费用与保证金计算结果
     */
    ClearingCalculationResult calculate(ClearingCalculationCommand command);
}
