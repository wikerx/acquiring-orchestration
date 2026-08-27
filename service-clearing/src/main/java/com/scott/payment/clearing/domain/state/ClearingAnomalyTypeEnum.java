package com.scott.payment.clearing.domain.state;

/** 清分异常案件分类；分类值同时作为低基数指标标签。 */
public enum ClearingAnomalyTypeEnum {
    CONTROLLED_FAILURE,
    FINANCIAL_MISMATCH,
    PROJECTION_MISMATCH,
    MANUAL_REVIEW
}
