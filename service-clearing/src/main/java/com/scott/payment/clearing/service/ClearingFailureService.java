package com.scott.payment.clearing.service;

import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.dto.ClearingFailureResult;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingFailureService
 * @date : 2026-08-26 16:00
 * @email : scott_x@163.com
 * @description : 清分受控失败的独立事务边界，原子提交失败状态与可选延时 Outbox；未知技术异常不由该边界吞掉。
 * @status : create
 */
public interface ClearingFailureService {

    /**
     * 提交当前 PROCESSING 租约的受控失败。
     *
     * @param message 当前交易终态或清分重试消息
     * @param claim 阶段 A 成功领取结果
     * @param processingOwner 当前租约持有者
     * @param failure 带稳定失败码的受控异常
     * @param nowUtc 本次失败提交的 UTC 时间
     * @return 已提交状态及延时重试结果
     */
    ClearingFailureResult recordFailure(PaymentTransactionEventMessage message,
                                        ClearingClaimResult claim,
                                        String processingOwner,
                                        ClearingProcessingException failure,
                                        LocalDateTime nowUtc);
}
