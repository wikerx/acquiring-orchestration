package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.mq.message.BaseMqMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IndependentReliableMqPublisher
 * @date : 2026-08-02 22:15
 * @email : scott_x@163.com
 * @description : 冻结安全、登录和操作审计消息，并在外层事务结束后使用独立事务可靠入队
 * @status : create
 */
@Slf4j
@Service
public class IndependentReliableMqPublisher {

    /** 当前事务型可靠发布器。 */
    private final ReliableMqPublisher delegate;
    /** 事务完成后执行独立 Outbox 持久化的受控线程池。 */
    private final TaskExecutor outboxExecutor;

    /** 创建独立事务可靠发布器。 */
    public IndependentReliableMqPublisher(
            ReliableMqPublisher delegate,
            @Qualifier("reliableMqOutboxRelayExecutor") TaskExecutor outboxExecutor) {
        this.delegate = delegate;
        this.outboxExecutor = outboxExecutor;
    }

    /**
     * 冻结审计消息，并在调用方事务结束后使用独立主库事务保存。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param message 已脱敏审计消息
     * @return 稳定消息编号
     */
    public String publish(String topic, String tag, BaseMqMessage message) {
        ReliableMqOutboxDO event = delegate.prepareEvent(topic, tag, message);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return delegate.publishPreparedEvent(event);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    schedulePersistence(event);
                }
            });
        } else {
            schedulePersistence(event);
        }
        return event.getEventId();
    }

    /** 将冻结消息交给独立线程，避免调用方事务连接与独立事务形成连接池嵌套占用。 */
    private void schedulePersistence(ReliableMqOutboxDO event) {
        try {
            outboxExecutor.execute(() -> persistSafely(event));
        } catch (RuntimeException exception) {
            log.warn("event: INDEPENDENT_MQ_OUTBOX_SCHEDULE_REJECTED eventId: {} exceptionType: {}",
                    event.getEventId(), exception.getClass().getSimpleName());
            persistSafely(event);
        }
    }

    /** 保存冻结消息；异常只记录类型，不能覆盖原业务结果或泄露消息载荷。 */
    private void persistSafely(ReliableMqOutboxDO event) {
        try {
            delegate.publishPreparedEvent(event);
        } catch (RuntimeException exception) {
            log.warn("event: INDEPENDENT_MQ_OUTBOX_PERSIST_FAILED eventId: {} exceptionType: {}",
                    event.getEventId(), exception.getClass().getSimpleName());
        }
    }
}
