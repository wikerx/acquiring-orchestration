package com.scott.payment.clearing.service;

import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingPersistenceService
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 清分两段事务的持久化边界；阶段A只领取租约，阶段B后续原子写入财务事实和成功幂等。
 * @status : create
 */
public interface ClearingPersistenceService {

    /**
     * 在 transaction 复合数据源的独立短事务内领取当前动作。
     *
     * @param message 已通过基础契约校验的清分输入消息
     * @param processingOwner 当前实例和线程租约标识
     * @param now 本次领取业务时间
     * @return 领取、重复消费、已完成或竞争失败结果
     */
    ClearingClaimResult claim(PaymentTransactionEventMessage message,
                              String processingOwner,
                              LocalDateTime now);
}
