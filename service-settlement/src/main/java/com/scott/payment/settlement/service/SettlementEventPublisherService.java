package com.scott.payment.settlement.service;

import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/** 将冻结 Outbox JSON 按 operationId 有序发布；发送失败只影响 Outbox，不影响已提交资金。 */
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

    /** @return true 表示领取了一条事件，false 表示当前无事件。 */
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
