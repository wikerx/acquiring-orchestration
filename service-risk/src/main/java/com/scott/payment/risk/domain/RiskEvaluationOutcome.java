package com.scott.payment.risk.domain;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationOutcome
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控决策和命中明细。
 * @status : create
 */
@Data
public class RiskEvaluationOutcome {

    /**
     * 对支付核心返回的聚合风控决策。
     */
    private RiskPaymentEvaluateResultDTO result;

    /**
     * 生成该决策的规则命中明细副本。
     */
    private List<RiskListMatch> hits = new ArrayList<>();

    /**
     * 创建不可共享调用方集合引用的风控结果。
     *
     * @param result 聚合决策
     * @param hits   命中明细；为空时使用空列表
     * @return 风控决策和命中明细
     */
    public static RiskEvaluationOutcome of(RiskPaymentEvaluateResultDTO result, List<RiskListMatch> hits) {
        RiskEvaluationOutcome outcome = new RiskEvaluationOutcome();
        outcome.setResult(result);
        outcome.setHits(hits == null ? new ArrayList<>() : new ArrayList<>(hits));
        return outcome;
    }
}
