package com.scott.payment.risk.domain;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FrequencySuccessReservationResult
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 单个频控维度成功名额的原子预占结果。
 * @status : create
 *
 *
 * @param outcome 预占结果类型
 * @param currentCount 当前已预占或确认的成功交易数
 */
public record FrequencySuccessReservationResult(Outcome outcome, long currentCount) {

    /**
     * 频控成功名额预占结果类型。
     */
    public enum Outcome {
        /** 本次交易首次获得成功名额。 */
        RESERVED,
        /** 相同交易已持有同一名额。 */
        IDEMPOTENT,
        /** 当前成功名额已达到规则上限。 */
        LIMIT_EXCEEDED,
        /** 交易生命周期已经关闭，禁止新增名额。 */
        CLOSED,
        /** Redis 或脚本不可用，调用方必须进入人工复核。 */
        UNAVAILABLE
    }

    /**
     * 创建基础设施不可用结果。
     *
     * @return 不携带计数的不可用结果
     */
    public static FrequencySuccessReservationResult unavailable() {
        return new FrequencySuccessReservationResult(Outcome.UNAVAILABLE, 0L);
    }
}
