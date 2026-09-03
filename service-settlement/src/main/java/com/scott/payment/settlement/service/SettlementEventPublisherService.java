package com.scott.payment.settlement.service;

import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementEventPublisherService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 发布冻结的结算 Outbox JSON；按 operationId 发送顺序消息，失败仅驱动 Outbox 重试，不回滚已经提交的资金事务。
 * @status : create
 */
@Service
public class SettlementEventPublisherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementEventPublisherService.class);

    private final SettlementEventOutboxPersistenceService persistenceService;
    private final MqProducer mqProducer;

    public SettlementEventPublisherService(SettlementEventOutboxPersistenceService persistenceService,
                                           MqProducer mqProducer) {
        this.persistenceService = persistenceService;
        this.mqProducer = mqProducer;
    }

    /**
     * 领取一条冻结 Outbox，按 operationId 顺序组至少一次发送并提交成功或退避状态。
     *
     * @param now 本轮 Outbox 认领时间
     * @return true 表示领取了一条事件，false 表示当前无事件
     */
    public boolean publishNext(LocalDateTime now) {
        Optional<SettlementEventOutboxDO> claimed = persistenceService.claimNext(now);
        if (claimed.isEmpty()) {
            return false;
        }
        SettlementEventOutboxDO row = claimed.get();
        try {
            mqProducer.sendSerializedOrderly(row.getTopic(), row.getTag(), row.getMessageKey(),
                    row.getSettlementBatchNo(), row.getRetryCount(), row.getPayloadJson(),
                    row.getMessageGroup());
            if (!persistenceService.markSent(row, LocalDateTime.now())) {
                throw new IllegalStateException("settlement event outbox sent-state CAS failed");
            }
        } catch (RuntimeException exception) {
            String code = exception.getClass().getSimpleName();
            if (!persistenceService.markFailed(row, code, LocalDateTime.now())) {
                LOGGER.error("Settlement event outbox failure-state CAS failed, eventNo={}",
                        row.getEventNo());
            }
            LOGGER.warn("Settlement event publication failed, eventNo={}, failureType={}",
                    row.getEventNo(), code);
        }
        return true;
    }
}
