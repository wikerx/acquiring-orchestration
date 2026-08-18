package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxStore;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqPublisher
 * @date : 2026-08-02 22:15
 * @email : scott_x@163.com
 * @description : 在调用方主库事务中冻结 MQ 消息并写入 Outbox，事务提交后触发一次即时投递
 * @status : create
 */
@Slf4j
@Service
public class ReliableMqPublisher {

    /** Outbox 持久化服务。 */
    private final ReliableMqOutboxStore outboxStore;
    /** Outbox Relay。 */
    private final ReliableMqOutboxRelayService relayService;
    /** 提交后即时 Relay 执行器，避免继续占用请求线程的事务连接。 */
    private final TaskExecutor relayExecutor;
    /** Outbox 配置。 */
    private final ReliableMqOutboxProperties properties;
    /** 当前生产服务名。 */
    private final String producerService;

    /** 创建可靠消息发布器。 */
    public ReliableMqPublisher(ReliableMqOutboxStore outboxStore,
                               ReliableMqOutboxRelayService relayService,
                               @Qualifier("reliableMqOutboxRelayExecutor") TaskExecutor relayExecutor,
                               ReliableMqOutboxProperties properties,
                               @Value("${spring.application.name:unknown-service}") String producerService) {
        this.outboxStore = outboxStore;
        this.relayService = relayService;
        this.relayExecutor = relayExecutor;
        this.properties = properties;
        this.producerService = producerService;
    }

    /**
     * 在当前业务事务中保存消息；无外层事务时自动创建事务。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param message 已脱敏消息
     * @return 稳定消息编号
     */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public String publish(String topic, String tag, BaseMqMessage message) {
        return publishPreparedEvent(prepareEvent(topic, tag, message));
    }

    /**
     * 持久化已经冻结的 Outbox 消息。
     *
     * <p>普通发布路径通过类内调用继续加入业务事务；独立审计发布器从 Bean 代理调用时使用
     * {@code REQUIRES_NEW}，且只会在原事务结束后执行，避免嵌套事务争抢连接。</p>
     *
     * @param event 已冻结且包含稳定事件号的消息快照
     * @return 稳定消息编号
     */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String publishPreparedEvent(ReliableMqOutboxDO event) {
        Objects.requireNonNull(event, "prepared mq outbox event can not be null");
        persistEvent(event);
        relayAfterCommit(event.getEventId());
        return event.getEventId();
    }

    /** 在进入异步边界前补齐元数据并冻结不可变 JSON 载荷。 */
    ReliableMqOutboxDO prepareEvent(String topic, String tag, BaseMqMessage message) {
        Objects.requireNonNull(message, "mq message can not be null");
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("mq topic can not be blank");
        }
        fillMetadata(message);
        return buildEvent(topic, tag, message);
    }

    /** 使用事件唯一键保证重复发布只接受相同业务快照。 */
    private void persistEvent(ReliableMqOutboxDO event) {
        try {
            outboxStore.insert(event);
        } catch (DuplicateKeyException duplicateKeyException) {
            verifyExistingEvent(event, duplicateKeyException);
        }
    }

    /** 补齐消息唯一编号、创建时间、traceId 和初始重试次数。 */
    private void fillMetadata(BaseMqMessage message) {
        if (!StringUtils.hasText(message.getMessageId())) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
        if (!StringUtils.hasText(message.getTraceId())) {
            message.setTraceId(TraceContext.getOrCreateTraceId());
        }
        if (message.getRetryCount() == null || message.getRetryCount() < 0) {
            message.setRetryCount(0);
        }
    }

    /** 构造不可变 Outbox 消息快照。 */
    private ReliableMqOutboxDO buildEvent(String topic, String tag, BaseMqMessage message) {
        LocalDateTime now = LocalDateTime.now();
        ReliableMqOutboxDO event = new ReliableMqOutboxDO();
        event.setEventId(message.getMessageId());
        event.setTopic(topic);
        event.setTag(tag);
        event.setProducerService(producerService);
        event.setTraceId(message.getTraceId());
        event.setPayloadJson(JsonUtils.toJsonString(message));
        event.setEventStatus("INIT");
        event.setRetryCount(0);
        event.setMaxRetryCount(Math.max(properties.getMaxRetryCount(), 1));
        event.setVersion(0);
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    /**
     * 校验重复事件号仍指向同一业务消息。
     *
     * <p>创建时间、traceId 和投递重试次数属于本次发布元数据，不参与业务快照比较；
     * Topic、Tag、生产服务或业务载荷变化时必须失败，禁止静默复用旧事件。</p>
     *
     * @param expected 本次准备写入的消息快照
     * @param duplicateKeyException 数据库唯一键异常
     */
    private void verifyExistingEvent(ReliableMqOutboxDO expected,
                                     DuplicateKeyException duplicateKeyException) {
        ReliableMqOutboxDO existing = outboxStore.findByEventId(expected.getEventId());
        boolean sameEvent = existing != null
                && Objects.equals(existing.getTopic(), expected.getTopic())
                && Objects.equals(existing.getTag(), expected.getTag())
                && Objects.equals(existing.getProducerService(), expected.getProducerService())
                && Objects.equals(semanticPayload(existing.getPayloadJson()), semanticPayload(expected.getPayloadJson()));
        if (!sameEvent) {
            throw new IllegalStateException(
                    "mq outbox event id conflicts with a different message",
                    duplicateKeyException);
        }
    }

    /** 返回去除发布元数据后的业务载荷，用于重复事件冲突判断。 */
    private Map<Object, Object> semanticPayload(String payloadJson) {
        Map<?, ?> parsed = JsonUtils.parseObject(payloadJson, Map.class);
        if (parsed == null) {
            return Map.of();
        }
        Map<Object, Object> payload = new HashMap<>(parsed);
        payload.remove("createdAt");
        payload.remove("traceId");
        payload.remove("retryCount");
        return payload;
    }

    /** 仅在数据库事务成功提交后触发 MQ 投递，失败时由定时 Relay 恢复。 */
    private void relayAfterCommit(String eventId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            scheduleImmediateRelay(eventId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                scheduleImmediateRelay(eventId);
            }
        });
    }

    /**
     * 将即时投递移出事务提交线程；提交失败或线程池繁忙时由定时 Relay 继续处理持久化事件。
     *
     * @param eventId 已提交的 Outbox 事件号
     */
    private void scheduleImmediateRelay(String eventId) {
        try {
            relayExecutor.execute(() -> {
                try {
                    relayService.relayEvent(eventId);
                } catch (RuntimeException exception) {
                    log.warn("event: RELIABLE_MQ_IMMEDIATE_RELAY_FAILED eventId: {} exceptionType: {}",
                            eventId, exception.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException exception) {
            log.warn("event: RELIABLE_MQ_IMMEDIATE_RELAY_REJECTED eventId: {} exceptionType: {}",
                    eventId, exception.getClass().getSimpleName());
        }
    }
}
