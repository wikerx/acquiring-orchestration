package com.scott.payment.component.core.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionDataUnavailableException
 * @date : 2026-08-03 00:00
 * @email : scott_x@163.com
 * @description : 标识查询时间范围触及未发布交易季度节点，供 Web 边界与内部调用统一转换为明确的可用性错误。
 * @status : create
 */
public class TransactionDataUnavailableException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** 发生路由缺口的交易逻辑表名，不包含物理表名。 */
    private final String logicalTable;
    /** 未通过治理校验并发布的自然季度，格式为 yyyy-Qn。 */
    private final String quarter;
    /** 触发路由失败的脱敏规则版本。 */
    private final String ruleVersion;

    /**
     * 创建交易季度节点不可用异常。
     *
     * @param logicalTable 交易逻辑表名
     * @param quarter 缺失的自然季度
     * @param ruleVersion 当前规则版本
     */
    public TransactionDataUnavailableException(String logicalTable, String quarter, String ruleVersion) {
        super("missing verified sharding node for " + logicalTable + " quarter " + quarter + " rule " + ruleVersion);
        this.logicalTable = logicalTable;
        this.quarter = quarter;
        this.ruleVersion = ruleVersion;
    }

    /** @return 发生路由缺口的交易逻辑表名 */
    public String getLogicalTable() {
        return logicalTable;
    }

    /** @return 未发布的自然季度 */
    public String getQuarter() {
        return quarter;
    }

    /** @return 当前规则版本 */
    public String getRuleVersion() {
        return ruleVersion;
    }
}
