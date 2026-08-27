package com.scott.payment.clearing.application;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingProcessingResult
 * @date : 2026-08-26 16:20
 * @email : scott_x@163.com
 * @description : 清分消息应用编排的低基数结果，用于消费者日志和ACK决策，不替代数据库清分状态。
 * @status : create
 */
public enum ClearingProcessingResult {
    /** 当前动作已完成清分阶段 B 提交。 */
    COMPLETED,
    /** 同一消息的成功幂等记录已存在。 */
    ALREADY_CONSUMED,
    /** 动作清分已经处于不可逆完成状态。 */
    ALREADY_COMPLETED,
    /** 原终态消息重投已由现存业务延时重试覆盖。 */
    RETRY_ALREADY_SCHEDULED,
    /** 动作处于人工复核状态，自动消息已安全确认。 */
    MANUAL_REVIEW_ACKNOWLEDGED,
    /** 过期延时消息已确认无需执行。 */
    STALE_RETRY_ACKNOWLEDGED,
    /** 受控失败状态及可选重试 Outbox 已提交。 */
    CONTROLLED_FAILURE_RECORDED
}
