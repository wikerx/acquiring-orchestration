package com.scott.payment.clearing.domain.state;

/**
 * 清分失败分类和重试属性，避免异常处理使用散落字符串或把确定性资金风险无限重试。
 */
public enum ClearingFailureCodeEnum {
    /**
     * 尚未读取到消息对应的权威交易动作，允许等待主库事实可见后重试。
     */
    TRANSACTION_NOT_FOUND(true),
    /**
     * 权威交易仍非终态，当前事件不具备清分资格。
     */
    TRANSACTION_NOT_TERMINAL(false),
    /**
     * 动作或财务状态版本发生并发变化，可重新读取权威事实后重试。
     */
    TRANSACTION_VERSION_CONFLICT(true),
    /**
     * 交易费用快照尚未生成或暂不可见，允许等待后重试。
     */
    FEE_SNAPSHOT_MISSING(true),
    /**
     * 冻结费用快照与持久化哈希不一致，必须阻断并人工核查数据完整性。
     */
    FEE_SNAPSHOT_HASH_MISMATCH(false),
    /**
     * 交易锁定的费用版本暂未查询到，允许等待主从或事务可见性后重试。
     */
    FEE_VERSION_NOT_FOUND(true),
    /**
     * 同一费用版本包含混合身份或冲突条款，不能作为不可变计算依据。
     */
    FEE_VERSION_NOT_IMMUTABLE(false),
    /**
     * 冻结费用版本缺少当前交易所需的有效规则。
     */
    FEE_RULE_NOT_CONFIGURED(false),
    /**
     * 冻结费用版本存在重复业务维度，继续计算会造成重复收费。
     */
    FEE_RULE_AMBIGUOUS(false),
    /**
     * 交易结算工作日日历缺失或未确认，禁止猜算可结算日。
     */
    SETTLEMENT_CALENDAR_UNAVAILABLE(true),
    /**
     * 权威金额为空、为负或不符合 ISO 币种指数，不能生成财务事实。
     */
    AMOUNT_INVALID(false),
    /**
     * 费用或返费组件的币种、指数与冻结来源事实不一致。
     */
    FEE_COMPONENT_CURRENCY_INVALID(false),
    /**
     * 退款、拒付或冲正依赖的源交易仍在清分处理中，允许延后重试。
     */
    SOURCE_CLEARING_PENDING(true),
    /**
     * 尚未找到退款、拒付或冲正依赖的源清分事实，允许等待源事实可见。
     */
    SOURCE_CLEARING_NOT_FOUND(true),
    /**
     * 结算候选依赖的源候选尚未完成入账，当前候选必须继续等待。
     */
    SOURCE_SETTLEMENT_PENDING(true),
    /**
     * 阶梯累计行版本发生并发变化，可重新锁定累计事实后重试。
     */
    TIER_ACCUMULATOR_CONFLICT(true),
    /**
     * 退款或释放所需的原保证金扣留事实尚未找到，允许等待后重试。
     */
    RESERVE_SOURCE_NOT_FOUND(true),
    /**
     * 本次保证金返还会超过原扣留或当前剩余金额，属于确定性资金校验失败。
     */
    RESERVE_RETURN_EXCEEDED(false),
    /**
     * 保证金状态或版本发生并发变化，可重新读取权威余额后重试。
     */
    RESERVE_STATE_CONFLICT(true),
    /**
     * 清分处理超时进入补偿流程，由恢复任务重新排期执行。
     */
    CLEARING_COMPENSATION_DUE(true),
    /**
     * 运营人员确认后发起的人工重试，用于审计和重试事件归因。
     */
    CLEARING_MANUAL_RETRY(true),
    /**
     * 清分条件更新未命中预期版本，说明并发处理已推进状态。
     */
    CLEARING_CAS_CONFLICT(true),
    /**
     * 清分事实、状态或可靠事件持久化发生瞬时技术失败。
     */
    CLEARING_PERSISTENCE_ERROR(true),
    /**
     * 有上限自动重试次数已经耗尽，必须转人工复核。
     */
    CLEARING_RETRY_EXHAUSTED(false);

    /**
     * 是否允许进入有上限的自动延时重试；确定性资金校验失败固定为 false。
     */
    private final boolean retryable;

    /**
     * 创建清分失败码并冻结其是否允许业务延时重试。
     */
    ClearingFailureCodeEnum(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * 判断该失败是否允许进入有上限的业务延时重试。
     *
     * @return 是否允许进入有上限的业务延时重试
     */
    public boolean isRetryable() {
        return retryable;
    }
}
