package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.application.SettlementAutomaticPostApplicationService;
import com.scott.payment.settlement.application.SettlementReviewOrderApplicationService;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReviewCreateCommand;
import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import com.scott.payment.settlement.service.SettlementAutomaticBatchService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementAutomaticBatchService
 * @date : 2026-08-26 22:10
 * @email : scott_x@163.com
 * @description : 按商户业务时区计算最近成熟日切，并将 AUTO_POST 分组交给主库事务锁定真实候选、幂等建批和有界认领。
 * @status : create
 */
@Service
public class DefaultSettlementAutomaticBatchService implements SettlementAutomaticBatchService {

    /**
     * 分组页大小，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int GROUP_PAGE_SIZE = 100;
    /**
     * {@code REVIEW_CANDIDATE_LIMIT}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int REVIEW_CANDIDATE_LIMIT = 1000;

    private final MerchantSettlementProfileMapper profileMapper;
    private final SettlementAutomaticPostApplicationService automaticPostService;
    private final SettlementReviewOrderApplicationService reviewOrderService;

    /**
     * 创建自动结算分组调度服务。
     *
     * @param profileMapper 自动结算档案和 READY 候选分组查询 Mapper
     * @param automaticPostService AUTO_POST 主库事务编排器
     * @param reviewOrderService AUTO_REVIEW 预审单编排器
     */
    public DefaultSettlementAutomaticBatchService(MerchantSettlementProfileMapper profileMapper,
                                                  SettlementAutomaticPostApplicationService automaticPostService,
                                                  SettlementReviewOrderApplicationService reviewOrderService) {
        this.profileMapper = profileMapper;
        this.automaticPostService = automaticPostService;
        this.reviewOrderService = reviewOrderService;
    }

    /**
     * 每个档案只创建最近成熟业务日的批次；服务停机期间积压候选由该批一次承接，不补造空日批。
     *
     * @param now 当前绝对时间
     * @return 进入认领流程的批次数
     */
    @Override
    public int createAndClaimMaturedBatches(Instant now) {
        Objects.requireNonNull(now, "settlement automatic batch time is required");
        List<SettlementBatchGroupDO> groups = profileMapper.selectReadyBatchGroups(GROUP_PAGE_SIZE);
        int processed = 0;
        for (SettlementBatchGroupDO group : groups) {
            BatchWindow window = maturedWindow(group, now);
            if (group.getEarliestEligibleDate() == null
                    || group.getEarliestEligibleDate().isAfter(window.businessDate())) {
                continue;
            }
            boolean handled;
            if ("AUTO_POST".equals(group.getProcessingMode())) {
                handled = createAutomaticBatch(group, window, now);
            } else if ("AUTO_REVIEW".equals(group.getProcessingMode())) {
                handled = createReview(group, window, now);
            } else {
                throw new IllegalStateException("automatic settlement processing mode is invalid");
            }
            if (handled) {
                processed++;
            }
        }
        return processed;
    }

    /** 将单个 AUTO_POST 分组交给独立 transaction 主库事务锁定真实锚点并完成建批认领。 */
    private boolean createAutomaticBatch(SettlementBatchGroupDO group, BatchWindow window, Instant now) {
        LocalDateTime operationTime = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        return automaticPostService.createAndClaim(
                group, batchType(group), window.businessDate(),
                window.cutoffBeginUtc(), window.cutoffEndUtc(), operationTime);
    }

    /** 创建 AUTO_REVIEW 预审并使用固定系统主体留痕，候选仍需逐条版本锁定。 */
    private boolean createReview(SettlementBatchGroupDO group, BatchWindow window, Instant now) {
        SettlementBatchType batchType = batchType(group);
        List<SettlementCandidateDO> candidates = profileMapper.selectReadyReviewCandidates(
                group.getSettlementProfileId(), batchType.name(), window.businessDate(),
                window.cutoffEndUtc(), REVIEW_CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            return false;
        }
        long anchor = requireAnchor(candidates.get(0).getId());
        LocalDateTime operationTime = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        reviewOrderService.submitAutomatic(new SettlementReviewCreateCommand(
                "AUTO_REVIEW:" + batchType.name() + ":" + group.getSettlementProfileId()
                        + ":" + window.businessDate() + ":" + anchor,
                batchType, window.businessDate(), window.cutoffBeginUtc(), window.cutoffEndUtc(),
                candidates.stream().map(candidate ->
                        new SettlementReviewCreateCommand.CandidateReference(
                                candidate.getId(), requireVersion(candidate.getVersion()))).toList(),
                "automatic review generated by settlement profile",
                new SettlementOperatorSnapshot(0L, "service-settlement", "SYSTEM",
                        "127.0.0.1", "service-settlement-scheduler", operationTime)));
        return true;
    }

    /** 解析并限制自动处理允许的批次类型，REVERSAL 不得由自动候选分组创建。 */
    private SettlementBatchType batchType(SettlementBatchGroupDO group) {
        SettlementBatchType batchType;
        try {
            batchType = SettlementBatchType.valueOf(group.getBatchType());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("automatic settlement batch type is invalid", exception);
        }
        if (batchType != SettlementBatchType.REGULAR
                && batchType != SettlementBatchType.RESERVE_RELEASE
                && batchType != SettlementBatchType.ADJUSTMENT) {
            throw new IllegalStateException("automatic settlement batch type is unsupported");
        }
        return batchType;
    }

    /** 使用档案 IANA 时区和日切点计算最近已成熟窗口，不通过 YML/Nacos 业务开关改变日历。 */
    private BatchWindow maturedWindow(SettlementBatchGroupDO group, Instant now) {
        if (group == null || group.getSettlementProfileId() == null || group.getSettlementProfileId() <= 0
                || group.getSettlementAccountId() == null || group.getSettlementAccountId() <= 0
                || group.getBusinessTimeZone() == null || group.getDailyCutoffTime() == null) {
            throw new IllegalStateException("settlement batch group identity or calendar is incomplete");
        }
        ZoneId zoneId = ZoneId.of(group.getBusinessTimeZone());
        ZonedDateTime localNow = now.atZone(zoneId);
        LocalDate localDate = localNow.toLocalDate();
        LocalDate businessDate = localNow.toLocalTime().isBefore(group.getDailyCutoffTime())
                ? localDate.minusDays(1) : localDate;
        ZonedDateTime cutoffEnd = businessDate.atTime(group.getDailyCutoffTime()).atZone(zoneId);
        ZonedDateTime cutoffBegin = businessDate.minusDays(1)
                .atTime(group.getDailyCutoffTime()).atZone(zoneId);
        return new BatchWindow(
                businessDate,
                LocalDateTime.ofInstant(cutoffBegin.toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(cutoffEnd.toInstant(), ZoneOffset.UTC));
    }

    /** 要求自动分组锚点候选主键有效，用于确定性请求键。 */
    private long requireAnchor(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalStateException("settlement batch anchor candidate id is invalid");
        }
        return value;
    }

    /** 要求自动预审候选携带非负 CAS 版本。 */
    private long requireVersion(Long value) {
        if (value == null || value < 0) {
            throw new IllegalStateException("settlement candidate version is invalid");
        }
        return value;
    }

    private record BatchWindow(LocalDate businessDate,
                               LocalDateTime cutoffBeginUtc,
                               LocalDateTime cutoffEndUtc) {
    }
}
