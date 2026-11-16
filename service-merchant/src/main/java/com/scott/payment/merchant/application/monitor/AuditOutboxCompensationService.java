package com.scott.payment.merchant.application.monitor;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxStore;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.publisher.ReliableMqOutboxRelayService;
import com.scott.payment.merchant.mapper.AuditOutboxCompensationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 商户端非资金审计 Outbox 受控补偿服务，仅允许按原消息重放缺少最终事实的 Merchant 操作日志。
 */
@Slf4j
@Service
public class AuditOutboxCompensationService {

    /** 单批补偿硬上限。 */
    public static final int MAX_BATCH_SIZE = 5;
    /** 唯一允许的生产服务。 */
    private static final String MERCHANT_SERVICE = "service-merchant";
    /** 唯一允许的审计 Topic。 */
    private static final String MERCHANT_OPERATION_LOG_TOPIC = "acquiring_merchant_operation_log_topic";
    /** 消息声明来源。 */
    private static final String MERCHANT_SYSTEM = "MERCHANT";

    private final AuditOutboxCompensationMapper mapper;
    private final ReliableMqOutboxStore outboxStore;
    private final ReliableMqOutboxRelayService relayService;

    /**
     * 创建受控补偿服务。
     */
    public AuditOutboxCompensationService(AuditOutboxCompensationMapper mapper,
                                          ReliableMqOutboxStore outboxStore,
                                          ReliableMqOutboxRelayService relayService) {
        this.mapper = mapper;
        this.outboxStore = outboxStore;
        this.relayService = relayService;
    }

    /**
     * 只读预演候选，不改变 Outbox 或审计事实。
     */
    public AuditOutboxCompensationResult preview(int requestedBatchSize) {
        int batchSize = validateBatchSize(requestedBatchSize);
        List<ReliableMqOutboxDO> candidates = mapper.selectMissingMerchantOperationLogs(batchSize);
        List<String> eventIds = candidates.stream().map(ReliableMqOutboxDO::getEventId).toList();
        return new AuditOutboxCompensationResult(true, candidates.size(), 0, 0, eventIds, null);
    }

    /**
     * 对满足全部门槛的消息逐条执行 CLOSED CAS 恢复和原消息投递；首个异常立即停止。
     */
    public AuditOutboxCompensationResult recoverAndRelay(int requestedBatchSize, String recoveryReason) {
        int batchSize = validateBatchSize(requestedBatchSize);
        if (!StringUtils.hasText(recoveryReason)) {
            throw new IllegalArgumentException("recoveryReason is required");
        }
        List<ReliableMqOutboxDO> candidates = mapper.selectMissingMerchantOperationLogs(batchSize);
        List<String> eventIds = new ArrayList<>();
        int recovered = 0;
        int relayed = 0;
        String stopReason = null;
        for (ReliableMqOutboxDO candidate : candidates) {
            try {
                OperationLogMessage message = validateCandidate(candidate);
                String idempotentKey = resolveIdempotentKey(message);
                if (mapper.countOperationLogFact(idempotentKey) != 0) {
                    stopReason = "FINAL_FACT_ALREADY_EXISTS";
                    break;
                }
                boolean recoveredNow = outboxStore.recoverClosed(
                        candidate.getEventId(), candidate.getVersion(), recoveryReason, LocalDateTime.now());
                if (!recoveredNow) {
                    stopReason = "OUTBOX_CAS_CONFLICT";
                    break;
                }
                recovered++;
                eventIds.add(candidate.getEventId());
                if (!relayService.relayEvent(candidate.getEventId())) {
                    stopReason = "RELAY_NOT_CONFIRMED";
                    break;
                }
                relayed++;
                log.info("商户端非资金审计 Outbox 补偿已投递, event: MERCHANT_AUDIT_OUTBOX_COMPENSATION_RELAYED, eventId: {}, topic: {}, batchPosition: {}",
                        candidate.getEventId(), candidate.getTopic(), recovered);
            } catch (RuntimeException exception) {
                stopReason = "VALIDATION_OR_RELAY_EXCEPTION:" + exception.getClass().getSimpleName();
                log.error("商户端非资金审计 Outbox 补偿异常并停止批次, event: MERCHANT_AUDIT_OUTBOX_COMPENSATION_STOPPED, eventId: {}, exceptionType: {}",
                        candidate == null ? null : candidate.getEventId(), exception.getClass().getSimpleName());
                break;
            }
        }
        return new AuditOutboxCompensationResult(false, candidates.size(), recovered, relayed,
                List.copyOf(eventIds), stopReason);
    }

    /** 校验单批数量，禁止通过配置突破 5 条硬上限。 */
    private int validateBatchSize(int requestedBatchSize) {
        if (requestedBatchSize < 1 || requestedBatchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batch size must be between 1 and " + MAX_BATCH_SIZE);
        }
        return requestedBatchSize;
    }

    /** 二次核对候选状态、路由、来源和载荷契约。 */
    private OperationLogMessage validateCandidate(ReliableMqOutboxDO candidate) {
        if (candidate == null
                || !MERCHANT_SERVICE.equals(candidate.getProducerService())
                || !MERCHANT_OPERATION_LOG_TOPIC.equals(candidate.getTopic())
                || candidate.getTag() != null
                || !"CLOSED".equals(candidate.getEventStatus())
                || candidate.getRetryCount() == null
                || candidate.getMaxRetryCount() == null
                || candidate.getRetryCount() < candidate.getMaxRetryCount()
                || !StringUtils.hasText(candidate.getPayloadJson())) {
            throw new IllegalStateException("candidate no longer satisfies compensation gates");
        }
        OperationLogMessage message = JsonUtils.parseObject(candidate.getPayloadJson(), OperationLogMessage.class);
        if (message == null || !MERCHANT_SYSTEM.equals(message.getSystemCode())
                || !StringUtils.hasText(message.getMessageId())) {
            throw new IllegalStateException("operation log payload contract is invalid");
        }
        return message;
    }

    /** 按消费者相同规则计算数据库最终幂等键。 */
    private String resolveIdempotentKey(OperationLogMessage message) {
        String key = StringUtils.hasText(message.getIdempotentKey())
                ? message.getIdempotentKey().trim()
                : MERCHANT_SYSTEM + ':' + message.getMessageId().trim();
        if (key.length() <= OperationLogMessage.IDEMPOTENT_KEY_MAX_LENGTH) {
            return key;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return MERCHANT_SYSTEM + ":sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 脱敏补偿结果，不包含消息载荷或操作日志业务字段。
     */
    public record AuditOutboxCompensationResult(boolean dryRun,
                                                 int candidateCount,
                                                 int recoveredCount,
                                                 int relayedCount,
                                                 List<String> eventIds,
                                                 String stopReason) {
    }
}
