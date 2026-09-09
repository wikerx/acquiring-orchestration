package com.scott.payment.clearing.dto;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingFailureResult
 * @date : 2026-08-26 16:00
 * @email : scott_x@163.com
 * @description : 清分受控失败提交结果，供应用编排层决定ACK并记录低基数状态，不包含异常正文或财务数据。
 * @status : create
 * @param targetStatus 已持久化的清分状态
 * @param recordedFailureCode 已持久化的稳定失败码
 * @param clearingRetryCount 当前累计业务重试序号
 * @param nextRetryTime 下一次业务重试 UTC 时间；无需重试时为空
 * @param retryScheduled 是否已在同一事务写入延时重试 Outbox
 */
public record ClearingFailureResult(String targetStatus,
                                    String recordedFailureCode,
                                    int clearingRetryCount,
                                    LocalDateTime nextRetryTime,
                                    boolean retryScheduled) {
}
