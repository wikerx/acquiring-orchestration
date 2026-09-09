package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.service.SettlementBatchFailureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransactionRollbackException;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementBatchFailureService
 * @date : 2026-08-26 23:40
 * @email : scott_x@163.com
 * @description : 依据稳定错误属性和数据库异常分类执行有界退避，耗尽或事实冲突时原子迁移批次及候选到人工复核。
 * @status : create
 */
@Service
public class DefaultSettlementBatchFailureService implements SettlementBatchFailureService {

    /**
     * {@code MAX_RETRY_COUNT}，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_RETRY_COUNT = 8;
    /**
     * {@code MAX_FAILURE_MESSAGE_LENGTH}常量，统一 {@code DefaultSettlementBatchFailureService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 512;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(6));

    private final SettlementBatchMapper batchMapper;
    private final SettlementCandidateMapper candidateMapper;
    private final SettlementBatchCandidateMapper relationMapper;

    public DefaultSettlementBatchFailureService(SettlementBatchMapper batchMapper,
                                                SettlementCandidateMapper candidateMapper,
                                                SettlementBatchCandidateMapper relationMapper) {
        this.batchMapper = batchMapper;
        this.candidateMapper = candidateMapper;
        this.relationMapper = relationMapper;
    }

    /**
     * 失败记录使用独立主库事务，不吞掉失败记录自身的数据库异常。
     *
     * @param settlementBatchNo 批次号
     * @param owner 当前租约所有者
     * @param failure 处理异常
     * @param now 失败记录时间
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(String settlementBatchNo,
                              String owner,
                              Throwable failure,
                              LocalDateTime now) {
        if (settlementBatchNo == null || settlementBatchNo.isBlank()
                || owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("settlement failure batch and owner are required");
        }
        Objects.requireNonNull(failure, "settlement processing failure is required");
        Objects.requireNonNull(now, "settlement failure time is required");
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(settlementBatchNo.trim());
        validateLease(batch, owner.trim(), now);

        FailureDescriptor descriptor = descriptor(failure);
        int retryCount = batch.getRetryCount() == null ? 0 : batch.getRetryCount();
        if (descriptor.retryable() && retryCount < MAX_RETRY_COUNT) {
            LocalDateTime nextRetryTime = now.plus(RETRY_DELAYS.get(
                    Math.min(retryCount, RETRY_DELAYS.size() - 1)));
            if (batchMapper.recordRetryableFailure(batch.getSettlementBatchNo(), owner.trim(),
                    retryCount, batch.getVersion(), descriptor.stage().name(), descriptor.code(),
                    descriptor.message(), now, nextRetryTime) != 1) {
                throw new IllegalStateException("settlement retryable failure state CAS failed");
            }
            return;
        }

        String code = descriptor.retryable() ? "SETTLEMENT_RETRY_EXHAUSTED" : descriptor.code();
        if (batchMapper.recordManualReview(batch.getSettlementBatchNo(), owner.trim(), retryCount,
                batch.getVersion(), descriptor.stage().name(), code, descriptor.message(), now) != 1) {
            throw new IllegalStateException("settlement manual review state CAS failed");
        }
        int expected = batch.getCandidateCount() == null ? 0 : batch.getCandidateCount();
        if (candidateMapper.markBatchManualReview(batch.getSettlementBatchNo(), now) != expected
                || relationMapper.markBatchManualReview(batch.getSettlementBatchNo(), now) != expected) {
            throw new IllegalStateException("settlement manual review candidate transition count is inconsistent");
        }
    }

    /** 将领域异常或瞬时 SQL 异常映射为稳定阶段、失败码、可重试性和脱敏摘要。 */
    private FailureDescriptor descriptor(Throwable failure) {
        if (failure instanceof SettlementProcessingException controlled) {
            return new FailureDescriptor(controlled.getStage(), controlled.getFailureCode(),
                    controlled.isRetryable(), sanitize(controlled.getMessage()));
        }
        if (containsTransientSql(failure)) {
            return new FailureDescriptor(SettlementFailureStage.RESULT_CALCULATION,
                    "SETTLEMENT_DATABASE_TRANSIENT", true,
                    "transient database failure while processing settlement batch");
        }
        return new FailureDescriptor(SettlementFailureStage.RESULT_CALCULATION,
                "SETTLEMENT_UNEXPECTED_FAILURE", true,
                "unexpected settlement processing failure: " + failure.getClass().getSimpleName());
    }

    /** 沿异常 cause 链识别死锁、锁等待等瞬时 SQL 异常，避免永久失败误判。 */
    private boolean containsTransientSql(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLTransientException
                    || current instanceof SQLRecoverableException
                    || current instanceof SQLTransactionRollbackException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 记录失败前要求处理租约仍归当前 owner 所有并未过期。 */
    private void validateLease(SettlementBatchDO batch, String owner, LocalDateTime now) {
        if (batch == null || batch.getVersion() == null || batch.getRetryCount() == null
                || !owner.equals(batch.getProcessingOwner())
                || batch.getProcessingDeadline() == null
                || !batch.getProcessingDeadline().isAfter(now)) {
            throw new IllegalStateException("settlement failure cannot be recorded after lease loss");
        }
    }

    /** 截断并规范化失败摘要，禁止将异常正文或敏感报文无限写入批次表。 */
    private String sanitize(String value) {
        String sanitized = value == null || value.isBlank() ? "settlement processing failed"
                : value.replace('\n', ' ').replace('\r', ' ').trim();
        return sanitized.length() <= MAX_FAILURE_MESSAGE_LENGTH
                ? sanitized : sanitized.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }

    private record FailureDescriptor(SettlementFailureStage stage,
                                     String code,
                                     boolean retryable,
                                     String message) {
    }
}
