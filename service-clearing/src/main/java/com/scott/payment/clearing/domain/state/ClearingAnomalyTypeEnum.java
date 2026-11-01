package com.scott.payment.clearing.domain.state;

/**
 * 清分异常案件分类；分类值同时作为低基数指标标签。
 */
public enum ClearingAnomalyTypeEnum {
    /**
     * 已映射为稳定失败码的业务或技术失败。
     */
    CONTROLLED_FAILURE,
    /**
     * 汇总金额与明细、保证金或来源事实不平。
     */
    FINANCIAL_MISMATCH,
    /**
     * 清分权威状态已提交，但查询投影未同步到相同版本。
     */
    PROJECTION_MISMATCH,
    /**
     * 自动恢复条件不满足，需要运营人员核查并决定后续动作。
     */
    MANUAL_REVIEW
}
