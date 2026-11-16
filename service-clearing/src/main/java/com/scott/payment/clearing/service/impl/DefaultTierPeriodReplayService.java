package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO;
import com.scott.payment.clearing.entity.ClearingTransactionMerchantSnapshotDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionMerchantSnapshotMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingCompletionService;
import com.scott.payment.clearing.service.ClearingPreparationService;
import com.scott.payment.clearing.service.FeeConfigurationSnapshotService;
import com.scott.payment.clearing.service.TierPeriodReplayService;
import com.scott.payment.clearing.service.TierPeriodReplayTransactionService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.clearing.support.ClearingTimeSupport;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.component.db.constant.DataSourceName;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 编排阶梯期间重放申请、不可变费用版本闭包和逐项短事务；远程缓存或主从读取始终发生在资金事务外。
 */
@Service
public class DefaultTierPeriodReplayService implements TierPeriodReplayService {

    /**
     * 阶梯累计期间键使用的固定六位年份月份格式。
     */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("uuuuMM");

    private final ClearingTierPeriodReplayMapper replayMapper;
    private final TierPeriodReplayTransactionService transactionService;
    private final ClearingTransactionOperationMapper operationMapper;
    private final ClearingTransactionMerchantSnapshotMapper merchantSnapshotMapper;
    private final FeeConfigurationSnapshotService snapshotService;
    private final ClearingPreparationService preparationService;
    private final ClearingCompletionService completionService;
    private final ClearingOperationalMetrics metrics;

    /**
     * 创建阶梯期间重放服务，并注入控制表、权威事实、快照、重算与指标依赖。
     */
    public DefaultTierPeriodReplayService(ClearingTierPeriodReplayMapper replayMapper,
                                          TierPeriodReplayTransactionService transactionService,
                                          ClearingTransactionOperationMapper operationMapper,
                                          ClearingTransactionMerchantSnapshotMapper merchantSnapshotMapper,
                                          FeeConfigurationSnapshotService snapshotService,
                                          ClearingPreparationService preparationService,
                                          ClearingCompletionService completionService,
                                          ClearingOperationalMetrics metrics) {
        this.replayMapper = replayMapper;
        this.transactionService = transactionService;
        this.operationMapper = operationMapper;
        this.merchantSnapshotMapper = merchantSnapshotMapper;
        this.snapshotService = snapshotService;
        this.preparationService = preparationService;
        this.completionService = completionService;
        this.metrics = metrics;
    }

    /**
     * 创建阶梯期间重放申请，并冻结期间、商户和请求幂等身份。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReplayResult submit(SubmitCommand command) {
        validateSubmit(command);
        YearMonth period = parsePeriod(command.periodKey());
        LocalDateTime now = ClearingTimeSupport.fromInstant(command.requestedInstant());
        ClearingTierPeriodReplayDO row = replayRow(command, period, now);
        replayMapper.insertIdempotent(row);
        ClearingTierPeriodReplayDO persisted = replayMapper.selectByRequestKeyForUpdate(command.requestKey().trim());
        validateDuplicate(persisted, row);
        metrics.recordTierReplay("SUBMITTED");
        return result(persisted);
    }

    /**
     * 按复核决定批准或拒绝重放申请，批准时解析受影响规则集合。
     */
    @Override
    public ReplayResult review(ReviewCommand command) {
        validateReview(command);
        LocalDateTime now = ClearingTimeSupport.fromInstant(command.reviewInstant());
        if (command.decision() == ReviewDecision.REJECT) {
            ReplayResult result = transactionService.reject(command, now);
            metrics.recordTierReplay("REJECTED");
            return result;
        }
        ClearingTierPeriodReplayDO replay = replayMapper.selectByReplayNo(command.replayNo());
        if (replay == null) {
            throw new IllegalStateException("tier replay request does not exist");
        }
        FeeVersionSnapshot snapshot = snapshotService.loadForRecalculation(
                replay.getMerchantId(), replay.getFeePlanId(), replay.getFeePlanVersionId(), replay.getPeriodStart());
        List<Long> tierRuleIds = snapshot.rules().stream()
                .filter(rule -> rule.calculationRule().feeMode() == FeeMode.TIER)
                .map(FeeRuleConfigurationSnapshot::ruleId)
                .distinct().sorted().toList();
        if (tierRuleIds.isEmpty() || !tierRuleIds.contains(replay.getTriggerFeeRuleId())) {
            throw new IllegalStateException("trigger fee rule is not in the immutable tier rule closure");
        }
        ReplayResult result = transactionService.approve(command, tierRuleIds, now);
        metrics.recordTierReplay(result.status());
        return result;
    }

    /**
     * 按稳定顺序推进到期重放任务，单轮处理数量受调用上限约束。
     */
    @Override
    public int runDue(int limit, Instant nowInstant) {
        if (limit < 1 || limit > 100 || nowInstant == null) {
            throw new IllegalArgumentException("tier replay scan limit and time are invalid");
        }
        LocalDateTime now = ClearingTimeSupport.fromInstant(nowInstant);
        List<ClearingTierPeriodReplayDO> runnable = replayMapper.selectRunnable(now, limit);
        int completed = 0;
        if (runnable == null) {
            return 0;
        }
        for (ClearingTierPeriodReplayDO replay : runnable) {
            ClearingTierPeriodReplayItemDO item = replayMapper.selectNextItem(replay.getReplayNo(), now);
            if (item == null) {
                continue;
            }
            try {
                processItem(replay, item, now);
                completed++;
                metrics.recordTierReplay(item.getSequenceNo().equals(replay.getItemCount())
                        ? "COMPLETED" : "RUNNING");
            } catch (RuntimeException exception) {
                String code = exception instanceof ClearingProcessingException processing
                        ? processing.getFailureCode().name() : "TIER_REPLAY_ITEM_FAILED";
                String message = StringUtils.hasText(exception.getMessage())
                        ? exception.getMessage() : exception.getClass().getSimpleName();
                transactionService.recordFailure(replay.getReplayNo(), item, code,
                        message.length() > 400 ? message.substring(0, 400) : message, now);
                metrics.recordTierReplay("ITEM_FAILED");
            }
        }
        return completed;
    }

    /**
     * 使用冻结费用版本重算一个已领取项目，成功提交后由事务服务推进重放游标。
     */
    private void processItem(ClearingTierPeriodReplayDO replay,
                             ClearingTierPeriodReplayItemDO item,
                             LocalDateTime now) {
        ClearingTransactionOperationDO operation = operationMapper.selectByTransaction(
                item.getTransactionId(), item.getTransactionDateTime());
        ClearingTransactionMerchantSnapshotDO merchantSnapshot = merchantSnapshotMapper.selectByTransaction(
                item.getTransactionId(), item.getTransactionDateTime());
        validateItemFacts(replay, item, operation, merchantSnapshot);
        FeeVersionSnapshot targetSnapshot = snapshotService.loadForRecalculation(
                replay.getMerchantId(), replay.getFeePlanId(), replay.getFeePlanVersionId(),
                merchantSnapshot.getFeeSnapshotTime());
        ClearingOperationFacts facts = toFacts(operation);
        ClearingClaimResult claim = new ClearingClaimResult(ClearingClaimResult.Outcome.ACQUIRED,
                item.getFinanceStateId(), item.getExpectedClearingRevision(),
                item.getExpectedFinanceStateVersion(), facts);
        PaymentTransactionEventMessage message = replayMessage(replay, item, operation, now);
        CompletionCommand command = preparationService.prepareForRecalculation(
                message, claim, "tier-replay:" + replay.getReplayNo(), targetSnapshot);
        completionService.recalculateTierPeriod(command, replay.getReplayNo(), item.getSequenceNo(),
                item.getExpectedFinanceStateVersion(), item.getExpectedClearingRevision(), now);
    }

    /**
     * 执行前确认动作仍与冻结修订、版本、完成时间和未结算门禁一致。
     */
    private void validateItemFacts(ClearingTierPeriodReplayDO replay,
                                   ClearingTierPeriodReplayItemDO item,
                                   ClearingTransactionOperationDO operation,
                                   ClearingTransactionMerchantSnapshotDO snapshot) {
        if (operation == null || snapshot == null || snapshot.getFeeSnapshotTime() == null
                || !Objects.equals(operation.getTransactionId(), item.getTransactionId())
                || !Objects.equals(operation.getTransactionDateTime(), item.getTransactionDateTime())
                || !Objects.equals(operation.getMerchantId(), replay.getMerchantId())
                || !Objects.equals(snapshot.getTransactionId(), item.getTransactionId())
                || !Objects.equals(snapshot.getOperationId(), operation.getOperationId())
                || !Objects.equals(snapshot.getMerchantId(), replay.getMerchantId())) {
            throw new IllegalStateException("tier replay item operation or merchant snapshot is inconsistent");
        }
    }

    /**
     * 为单项重放构造确定性内部触发消息，不生成新的支付交易或终态。
     */
    private PaymentTransactionEventMessage replayMessage(ClearingTierPeriodReplayDO replay,
                                                          ClearingTierPeriodReplayItemDO item,
                                                          ClearingTransactionOperationDO operation,
                                                          LocalDateTime now) {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("TIER_REPLAY:" + replay.getReplayNo() + ":" + item.getSequenceNo());
        message.setCreatedAt(now);
        message.setRetryCount(item.getAttemptCount());
        message.setTransactionId(operation.getTransactionId());
        message.setOperationId(operation.getOperationId());
        message.setMerchantId(operation.getMerchantId());
        message.setMerchantOrderNo(operation.getMerchantOrderNo());
        message.setTransactionType(operation.getTransactionType());
        message.setTransactionStatus(operation.getTransactionStatus());
        message.setEventType(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionDateTime(operation.getTransactionDateTime());
        return message;
    }

    /**
     * 将数据库动作记录转换为期间重放使用的不可变权威事实。
     */
    private ClearingOperationFacts toFacts(ClearingTransactionOperationDO row) {
        return new ClearingOperationFacts(row.getTransactionId(), row.getOperationId(),
                row.getSourceTransactionId(), row.getMerchantId(), row.getMerchantOrderNo(),
                row.getTransactionType(), row.getTransactionStatus(), row.getLabelCurrency(),
                row.getLabelAmount(), row.getApprovedCurrency(), row.getApprovedAmount(),
                row.getTransactionCurrency(), row.getTransactionAmount(), row.getCurrencyExponent(),
                row.getTransactionDateTime(), row.getTransactionUtcTime(), row.getTransactionTimeZone(),
                row.getVersion());
    }

    /**
     * 固化商户、不可变费用版本和月度半开窗口的重放控制行。
     */
    private ClearingTierPeriodReplayDO replayRow(SubmitCommand command,
                                                  YearMonth period,
                                                  LocalDateTime now) {
        ClearingTierPeriodReplayDO row = new ClearingTierPeriodReplayDO();
        row.setReplayNo(stableId(command.requestKey().trim()));
        row.setRequestKey(command.requestKey().trim());
        row.setMerchantId(command.merchantId().trim());
        row.setFeePlanId(command.feePlanId());
        row.setFeePlanVersionId(command.feePlanVersionId());
        row.setTriggerFeeRuleId(command.triggerFeeRuleId());
        row.setPeriodKey(command.periodKey().trim());
        row.setPeriodStart(period.atDay(1).atStartOfDay());
        row.setPeriodEnd(period.plusMonths(1).atDay(1).atStartOfDay());
        row.setReason(command.reason().trim());
        row.setSubmitOperator(command.submitOperator().trim());
        row.setReplayStatus("PENDING_REVIEW");
        row.setItemCount(0);
        row.setCompletedCount(0);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /**
     * 幂等请求键冲突后核对全部范围和提交审计，禁止扩大重放范围。
     */
    private void validateDuplicate(ClearingTierPeriodReplayDO actual,
                                   ClearingTierPeriodReplayDO expected) {
        if (actual == null || !Objects.equals(actual.getReplayNo(), expected.getReplayNo())
                || !Objects.equals(actual.getRequestKey(), expected.getRequestKey())
                || !Objects.equals(actual.getMerchantId(), expected.getMerchantId())
                || !Objects.equals(actual.getFeePlanId(), expected.getFeePlanId())
                || !Objects.equals(actual.getFeePlanVersionId(), expected.getFeePlanVersionId())
                || !Objects.equals(actual.getTriggerFeeRuleId(), expected.getTriggerFeeRuleId())
                || !Objects.equals(actual.getPeriodKey(), expected.getPeriodKey())) {
            throw new IllegalStateException("tier replay request key contains mismatched data");
        }
    }

    /**
     * 将期间重放控制行转换为提交、复核和查询共用的幂等结果。
     */
    private ReplayResult result(ClearingTierPeriodReplayDO row) {
        return new ReplayResult(row.getReplayNo(), row.getReplayStatus(), value(row.getItemCount()),
                value(row.getCompletedCount()), row.getVersion() == null ? 0L : row.getVersion());
    }

    /**
     * 将尚未初始化的项目或完成计数转换为零。
     */
    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 提交只接受明确商户、不可变版本、阶梯规则和 yyyy-MM 期间。
     */
    private void validateSubmit(SubmitCommand command) {
        if (command == null || !validText(command.requestKey(), 128)
                || !validText(command.merchantId(), 64) || command.feePlanId() < 1
                || command.feePlanVersionId() < 1 || command.triggerFeeRuleId() < 1
                || !validText(command.periodKey(), 6) || !validText(command.reason(), 400)
                || !validText(command.submitOperator(), 128) || command.requestedInstant() == null) {
            throw new IllegalArgumentException("complete tier replay submit command is required");
        }
        parsePeriod(command.periodKey());
    }

    /**
     * 复核命令要求申请版本、固定决定、可信复核人和审计时点。
     */
    private void validateReview(ReviewCommand command) {
        if (command == null || !validText(command.replayNo(), 64)
                || command.expectedRequestVersion() < 0 || command.decision() == null
                || !validText(command.reviewComment(), 400) || !validText(command.reviewOperator(), 128)
                || command.reviewInstant() == null) {
            throw new IllegalArgumentException("complete tier replay review command is required");
        }
    }

    /**
     * 将 yyyyMM 期间键解析为自然月，非法期间必须在创建控制行前拒绝。
     */
    private YearMonth parsePeriod(String value) {
        try {
            return YearMonth.parse(value == null ? "" : value.trim(), PERIOD_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("tier replay period key must use yyyyMM", exception);
        }
    }

    /**
     * 判断审计或身份文本是否非空且位于持久化长度限制内。
     */
    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }

    /**
     * 根据稳定业务身份生成可重复计算的幂等标识。
     *
     * @param requestKey 调用方提供的期间重放幂等请求键
     * @return 固定前缀加请求键 SHA-256 摘要前缀组成的重放编号
     */
    private String stableId(String requestKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requestKey.getBytes(StandardCharsets.UTF_8));
            return "TRP" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
