package com.scott.payment.payment.service.capability;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.id.GlobalIdValidator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.mapper.TransactionIdempotencyMapper;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 在远程风控前登记准备归属，并在交易事务中锁定同一行。归属记录不会进入交易查询。
 */
@Service
public class PaymentPreparationOwnershipService {

    private static final String HEAD_SCOPE = "PAYMENT_PREPARATION";
    private static final String CANDIDATE_SCOPE = "PAYMENT_PREPARATION_CANDIDATE";
    private static final String OPERATION_SCOPE = "TRANSACTION_OPERATION";
    private static final String ACTIVE = "PREPARING";
    private static final String ATTACHED = "ATTACHED";
    private static final String TERMINATED = "TERMINATED";
    private static final long LEASE_MINUTES = 2;
    private static final Pattern OWNERSHIP_KEY_FORMAT = Pattern.compile("[a-f0-9]{64}");

    private final TransactionIdempotencyMapper mapper;
    private final TransactionIdempotencyService idempotencyService;
    private final GlobalIdGenerator globalIdGenerator;

    public PaymentPreparationOwnershipService(TransactionIdempotencyMapper mapper,
                                               TransactionIdempotencyService idempotencyService,
                                               GlobalIdGenerator globalIdGenerator) {
        this.mapper = mapper;
        this.idempotencyService = idempotencyService;
        this.globalIdGenerator = globalIdGenerator;
    }

    /** 发号可能需要访问 Redis，只能在归属事务及其行锁之外调用。 */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.NEVER)
    public String nextCandidate() {
        String candidate = globalIdGenerator.nextId();
        if (!GlobalIdValidator.isValid(candidate)) {
            throw candidateUnavailable();
        }
        return candidate;
    }

    /**
     * 关闭门禁时可复用已有动作，但已有归属的动作继续走原受理收敛。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public TransactionIdempotencyDO readBeforeLegacyPreparation(String operationKey) {
        TransactionIdempotencyDO head = lock(HEAD_SCOPE, ownershipKey(operationKey));
        TransactionIdempotencyDO operation = lock(OPERATION_SCOPE, operationKey);
        if (operation == null && head != null) {
            throw inProgress();
        }
        return operation;
    }

    /** 数据库写入前再次核对，防止旧路径覆盖规划期间登记的受保护归属。 */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireLegacyPreparationAllowed(String operationKey) {
        if (lock(HEAD_SCOPE, ownershipKey(operationKey)) != null) {
            throw inProgress();
        }
    }

    /**
     * 在独立事务中取得交易准备归属，使并发同键请求看到同一占用或交易事实。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Acquisition acquire(PaymentCreateCommandDTO command,
                               String operationKey,
                               String transactionType,
                               String timeZone,
                               String candidateTransactionId) {
        String key = ownershipKey(operationKey);
        TransactionIdempotencyDO head = lock(HEAD_SCOPE, key);
        TransactionIdempotencyDO operation = lock(OPERATION_SCOPE, operationKey);
        if (operation != null) {
            return new Acquisition(null, operation);
        }
        if (head == null) {
            LocalDateTime now = mapper.currentDatabaseTime();
            LocalDateTime originalTime = atDatabasePrecision(command.getTransactionDateTime());
            // 首代及接管均使用独立全局号，收银台原候选号只作为恢复锚点。
            String checkoutCandidate = "HOSTED_CHECKOUT".equals(command.getRequestSource())
                    && StringUtils.hasText(command.getTransactionId()) ? command.getTransactionId() : null;
            TransactionIdempotencyDO initial = idempotencyService.newProcessingRecord(
                    HEAD_SCOPE, key, command.getMerchantId(), command.getMerchantOrderNo(),
                    command.getMerchantOrderId(), transactionType, originalTime, timeZone,
                    command.getRequestFingerprint(), now);
            initial.setTransactionStatus(ACTIVE);
            // 独立准备范围的 operation_id 记录收银台原始尝试锚点，接管不更改。
            initial.setOperationId(checkoutCandidate);
            initial.setVersion(1);
            initial.setExpireTime(now.plusMinutes(LEASE_MINUTES));
            reserveCandidate(initial, 1, candidateTransactionId, now);
            initial.setTransactionId(candidateTransactionId);
            try {
                requireOne(mapper.insert(initial));
            } catch (DuplicateKeyException concurrentClaim) {
                // 同事务撤销败方的候选号保留，由原键重放读取实际归属。
                throw inProgress();
            }
            return new Acquisition(toLease(initial, operationKey), null);
        }
        if (!Objects.equals(head.getRequestFingerprint(), command.getRequestFingerprint())
                || !Objects.equals(head.getMerchantId(), command.getMerchantId())
                || !Objects.equals(operationKey, originalOperationKey(head))
                || !sameCheckoutOrigin(head, command)) {
            throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT);
        }
        LocalDateTime now = mapper.currentDatabaseTime();
        if (ACTIVE.equals(head.getTransactionStatus()) && expired(head, now)) {
            terminate(head, now);
            head.setTransactionStatus(TERMINATED);
        }
        if (!TERMINATED.equals(head.getTransactionStatus())
                || head.getVersion() == null || head.getVersion() == Integer.MAX_VALUE) {
            throw inProgress();
        }
        int generation = head.getVersion() + 1;
        reserveCandidate(head, generation, candidateTransactionId, now);
        LocalDateTime expiration = now.plusMinutes(LEASE_MINUTES);
        requireOne(mapper.update(null, currentUpdate(head)
                .set(TransactionIdempotencyDO::getTransactionStatus, ACTIVE)
                .set(TransactionIdempotencyDO::getVersion, generation)
                .set(TransactionIdempotencyDO::getTransactionId, candidateTransactionId)
                .set(TransactionIdempotencyDO::getExpireTime, expiration)
                .set(TransactionIdempotencyDO::getUpdateTime, now)));
        return new Acquisition(new Lease(key, operationKey, head.getMerchantId(), generation,
                candidateTransactionId, head.getTransactionDateTime(), head.getRequestFingerprint()), null);
    }

    /**
     * 在写入操作、交易和 outbox 前锁住本代，直到同一个 TRANSACTION 事务完成才释放锁。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCurrent(Lease lease) {
        TransactionIdempotencyDO head = lock(HEAD_SCOPE, lease.key());
        if (!matches(head, lease) || !ACTIVE.equals(head.getTransactionStatus())
                || head.getExpireTime() == null || expired(head, mapper.currentDatabaseTime())) {
            throw inProgress();
        }
    }

    /**
     * 将已取得的准备归属绑定到本事务创建的交易动作。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.MANDATORY)
    public void attach(Lease lease) {
        TransactionIdempotencyDO head = lock(HEAD_SCOPE, lease.key());
        if (!matches(head, lease) || !ACTIVE.equals(head.getTransactionStatus())) {
            throw inProgress();
        }
        LocalDateTime now = mapper.currentDatabaseTime();
        transitionCandidate(head, ATTACHED, now);
        requireOne(mapper.update(null, currentUpdate(head)
                .set(TransactionIdempotencyDO::getTransactionStatus, ATTACHED)
                .set(TransactionIdempotencyDO::getUpdateTime, now)));
    }

    /**
     * 在独立读事务中回读已经绑定且提交的交易动作。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionIdempotencyDO readCommittedOperation(Lease lease) {
        TransactionIdempotencyDO head = lock(HEAD_SCOPE, lease.key());
        if (!matches(head, lease) || !ATTACHED.equals(head.getTransactionStatus())) {
            return null;
        }
        TransactionIdempotencyDO operation = lock(OPERATION_SCOPE, lease.operationKey());
        return sameCommittedCandidate(head, operation) ? operation : null;
    }

    /**
     * 按原收银台尝试核对主库。超时只允许经同一归属行锁终止，未知证据继续等待。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CheckoutRecovery recoverCheckoutPreparation(String merchantId,
                                                        String merchantOrderNo,
                                                        String attemptRequestId,
                                                        String transactionType,
                                                        String checkoutCandidate,
                                                        String checkoutOperationId,
                                                        LocalDateTime checkoutTime,
                                                        boolean requireExpiredLease) {
        if (!StringUtils.hasText(checkoutCandidate) || checkoutTime == null) {
            return CheckoutRecovery.ACTIVE;
        }
        String operationKey = idempotencyService.buildTransactionOperationKey(
                merchantId, attemptRequestId, transactionType);
        TransactionIdempotencyDO head = lock(HEAD_SCOPE, ownershipKey(operationKey));
        if (head == null || !Objects.equals(head.getMerchantId(), merchantId)
                || !Objects.equals(head.getMerchantOrderNo(), merchantOrderNo)
                || !Objects.equals(head.getMerchantOrderId(), attemptRequestId)
                || !StringUtils.hasText(head.getOperationId())
                || !StringUtils.hasText(head.getRequestFingerprint())
                || !Objects.equals(head.getTransactionDateTime(), atDatabasePrecision(checkoutTime))) {
            return CheckoutRecovery.ACTIVE;
        }
        boolean originalAttempt = Objects.equals(head.getOperationId(), checkoutCandidate);
        if (!originalAttempt && !Objects.equals(head.getTransactionId(), checkoutCandidate)) {
            return CheckoutRecovery.ACTIVE;
        }
        LocalDateTime checkedAt = requireExpiredLease ? mapper.currentDatabaseTime() : null;
        if (requireExpiredLease && !expired(head, checkedAt)) {
            return CheckoutRecovery.ACTIVE;
        }
        TransactionIdempotencyDO operation = lock(OPERATION_SCOPE, operationKey);
        if (ATTACHED.equals(head.getTransactionStatus())) {
            if (sameCommittedCandidate(head, operation)
                    && (originalAttempt || Objects.equals(operation.getOperationId(), checkoutOperationId))) {
                return new CheckoutRecovery(CheckoutRecoveryState.ATTACHED, operation);
            }
            return CheckoutRecovery.ACTIVE;
        }
        if (!originalAttempt || operation != null) {
            return CheckoutRecovery.ACTIVE;
        }
        if (TERMINATED.equals(head.getTransactionStatus())) {
            return terminatedGenerationMatches(toLease(head, operationKey))
                    ? CheckoutRecovery.TERMINATED : CheckoutRecovery.ACTIVE;
        }
        if (ACTIVE.equals(head.getTransactionStatus())) {
            if (checkedAt == null) {
                checkedAt = mapper.currentDatabaseTime();
            }
            if (expired(head, checkedAt)) {
                terminate(head, checkedAt);
                return CheckoutRecovery.TERMINATED;
            }
        }
        return CheckoutRecovery.ACTIVE;
    }

    /** 仅用于当前请求已确认没有提交的分支。风控调用始终在本事务之外。 */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancellationDecision terminateUncommitted(Lease lease) {
        return reconcile(lease, true);
    }

    /**
     * 复核确切准备代次及交易事实后判定是否允许取消预占。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancellationDecision reconcileCancellation(String merchantId,
                                                        String ownershipKey,
                                                        long generation,
                                                        String candidateTransactionId,
                                                        LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(merchantId) || ownershipKey == null
                || !OWNERSHIP_KEY_FORMAT.matcher(ownershipKey).matches()
                || generation < 1 || generation > Integer.MAX_VALUE
                || !StringUtils.hasText(candidateTransactionId) || transactionDateTime == null) {
            return CancellationDecision.DEFER;
        }
        return reconcile(new Lease(ownershipKey, null, merchantId, (int) generation,
                candidateTransactionId, atDatabasePrecision(transactionDateTime), null), false);
    }

    private CancellationDecision reconcile(Lease lease, boolean rollbackConfirmed) {
        TransactionIdempotencyDO head = lock(HEAD_SCOPE, lease.key());
        if (!matches(head, lease)) {
            return terminatedGenerationMatches(lease) ? CancellationDecision.RELEASE_ALLOWED
                    : CancellationDecision.DEFER;
        }
        if (ATTACHED.equals(head.getTransactionStatus())) {
            return CancellationDecision.RETAIN;
        }
        if (TERMINATED.equals(head.getTransactionStatus())) {
            return terminatedGenerationMatches(lease) ? CancellationDecision.RELEASE_ALLOWED
                    : CancellationDecision.DEFER;
        }
        if (!ACTIVE.equals(head.getTransactionStatus()) || head.getExpireTime() == null) {
            return CancellationDecision.DEFER;
        }
        LocalDateTime now = mapper.currentDatabaseTime();
        if (!rollbackConfirmed && !expired(head, now)) {
            return CancellationDecision.RETAIN;
        }
        String operationKey = originalOperationKey(head);
        if (!Objects.equals(ownershipKey(operationKey), lease.key())) {
            return CancellationDecision.DEFER;
        }
        TransactionIdempotencyDO operation = lock(OPERATION_SCOPE, operationKey);
        if (operation != null && Objects.equals(operation.getTransactionId(), lease.candidateTransactionId())) {
            return CancellationDecision.RETAIN;
        }
        terminate(head, now);
        return CancellationDecision.RELEASE_ALLOWED;
    }

    /**
     * 为同一准备身份登记风险调用使用的候选时间与归属范围。
     */
    private void reserveCandidate(TransactionIdempotencyDO head, int generation, String candidate,
                                  LocalDateTime now) {
        if (!GlobalIdValidator.isValid(candidate) || Objects.equals(candidate, head.getOperationId())
                || Objects.equals(candidate, head.getTransactionId())) {
            throw candidateUnavailable();
        }
        TransactionIdempotencyDO receipt = idempotencyService.newProcessingRecord(
                CANDIDATE_SCOPE, candidate, head.getMerchantId(), head.getMerchantOrderNo(),
                head.getMerchantOrderId(), head.getTransactionType(), head.getTransactionDateTime(),
                head.getTransactionTimeZone(), head.getRequestFingerprint(), now);
        receipt.setTransactionStatus(ACTIVE);
        receipt.setTransactionId(candidate);
        receipt.setOperationId(head.getIdempotencyKey());
        receipt.setVersion(generation);
        // 保留号码唯一占用及终止凭据，处理迟到的预占。
        receipt.setExpireTime(null);
        try {
            requireOne(mapper.insert(receipt));
        } catch (DuplicateKeyException candidateUsed) {
            // 已用全局号说明编号权威存在冲突，回滚本次登记并停止业务。
            throw candidateUnavailable();
        }
    }

    private void terminate(TransactionIdempotencyDO head, LocalDateTime now) {
        transitionCandidate(head, TERMINATED, now);
        requireOne(mapper.update(null, currentUpdate(head)
                .set(TransactionIdempotencyDO::getTransactionStatus, TERMINATED)
                .set(TransactionIdempotencyDO::getUpdateTime, now)));
    }

    private void transitionCandidate(TransactionIdempotencyDO head, String status, LocalDateTime now) {
        requireOne(mapper.update(null, Wrappers.<TransactionIdempotencyDO>lambdaUpdate()
                .set(TransactionIdempotencyDO::getTransactionStatus, status)
                .set(TransactionIdempotencyDO::getUpdateTime, now)
                .eq(TransactionIdempotencyDO::getIdempotencyScope, CANDIDATE_SCOPE)
                .eq(TransactionIdempotencyDO::getIdempotencyKey, head.getTransactionId())
                .eq(TransactionIdempotencyDO::getOperationId, head.getIdempotencyKey())
                .eq(TransactionIdempotencyDO::getMerchantId, head.getMerchantId())
                .eq(TransactionIdempotencyDO::getVersion, head.getVersion())
                .eq(TransactionIdempotencyDO::getTransactionStatus, ACTIVE)
                .eq(TransactionIdempotencyDO::getDeleted, 0)));
    }

    private boolean terminatedGenerationMatches(Lease lease) {
        TransactionIdempotencyDO receipt = lock(CANDIDATE_SCOPE, lease.candidateTransactionId());
        return matches(receipt, lease) && TERMINATED.equals(receipt.getTransactionStatus())
                && Objects.equals(receipt.getOperationId(), lease.key());
    }

    private TransactionIdempotencyDO lock(String scope, String key) {
        return mapper.selectOne(Wrappers.<TransactionIdempotencyDO>lambdaQuery()
                .eq(TransactionIdempotencyDO::getIdempotencyScope, scope)
                .eq(TransactionIdempotencyDO::getIdempotencyKey, key)
                .eq(TransactionIdempotencyDO::getDeleted, 0)
                .last("FOR UPDATE"));
    }

    private static LambdaUpdateWrapper<TransactionIdempotencyDO> currentUpdate(TransactionIdempotencyDO head) {
        return Wrappers.<TransactionIdempotencyDO>lambdaUpdate()
                .eq(TransactionIdempotencyDO::getId, head.getId())
                .eq(TransactionIdempotencyDO::getVersion, head.getVersion())
                .eq(TransactionIdempotencyDO::getTransactionStatus, head.getTransactionStatus())
                .eq(TransactionIdempotencyDO::getDeleted, 0);
    }

    private String originalOperationKey(TransactionIdempotencyDO row) {
        return idempotencyService.buildTransactionOperationKey(
                row.getMerchantId(), row.getMerchantOrderId(), row.getTransactionType());
    }

    private static boolean matches(TransactionIdempotencyDO row, Lease lease) {
        return row != null && Objects.equals(row.getMerchantId(), lease.merchantId())
                && Objects.equals(row.getVersion(), lease.generation())
                && Objects.equals(row.getTransactionId(), lease.candidateTransactionId())
                && Objects.equals(row.getTransactionDateTime(), lease.transactionDateTime())
                && (lease.requestFingerprint() == null
                    || Objects.equals(row.getRequestFingerprint(), lease.requestFingerprint()));
    }

    private static boolean sameCheckoutOrigin(TransactionIdempotencyDO head, PaymentCreateCommandDTO command) {
        boolean checkout = "HOSTED_CHECKOUT".equals(command.getRequestSource());
        if (!StringUtils.hasText(head.getOperationId())) {
            return !checkout;
        }
        return checkout && (Objects.equals(head.getOperationId(), command.getTransactionId())
                || Objects.equals(head.getTransactionId(), command.getTransactionId()));
    }

    private static boolean sameCommittedCandidate(TransactionIdempotencyDO head, TransactionIdempotencyDO operation) {
        return operation != null && StringUtils.hasText(operation.getTransactionId())
                && StringUtils.hasText(operation.getOperationId())
                && Objects.equals(head.getMerchantId(), operation.getMerchantId())
                && Objects.equals(head.getMerchantOrderNo(), operation.getMerchantOrderNo())
                && Objects.equals(head.getMerchantOrderId(), operation.getMerchantOrderId())
                && Objects.equals(head.getTransactionId(), operation.getTransactionId())
                && Objects.equals(head.getTransactionDateTime(), operation.getTransactionDateTime())
                && Objects.equals(head.getRequestFingerprint(), operation.getRequestFingerprint());
    }

    private static boolean expired(TransactionIdempotencyDO row, LocalDateTime now) {
        return row.getExpireTime() != null && !now.isBefore(row.getExpireTime());
    }

    private static Lease toLease(TransactionIdempotencyDO row, String operationKey) {
        return new Lease(row.getIdempotencyKey(), operationKey, row.getMerchantId(), row.getVersion(),
                row.getTransactionId(), row.getTransactionDateTime(), row.getRequestFingerprint());
    }

    private static String ownershipKey(String originalOperationKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(originalOperationKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static LocalDateTime atDatabasePrecision(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireOne(int updated) {
        if (updated != 1) {
            throw inProgress();
        }
    }

    private static ServiceException inProgress() {
        return new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS);
    }

    private static ServiceException candidateUnavailable() {
        return new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                "payment preparation candidate identity is unavailable");
    }

    public enum CancellationDecision {
        RELEASE_ALLOWED, RETAIN, DEFER
    }

    public enum CheckoutRecoveryState {
        ACTIVE, ATTACHED, TERMINATED
    }

    public record CheckoutRecovery(CheckoutRecoveryState state, TransactionIdempotencyDO operation) {
        private static final CheckoutRecovery ACTIVE = new CheckoutRecovery(CheckoutRecoveryState.ACTIVE, null);
        private static final CheckoutRecovery TERMINATED = new CheckoutRecovery(CheckoutRecoveryState.TERMINATED, null);
    }

    public record Acquisition(Lease lease, TransactionIdempotencyDO committedOperation) {
    }

    public record Lease(String key,
                        String operationKey,
                        String merchantId,
                        int generation,
                        String candidateTransactionId,
                        LocalDateTime transactionDateTime,
                        String requestFingerprint) {
    }
}
