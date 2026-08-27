package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import com.scott.payment.settlement.service.SettlementAutomaticBatchService;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.service.SettlementCandidateBulkClaimService;
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
 * @description : 按商户业务时区计算最近成熟日切，以 profileId、业务日和首候选ID生成稳定幂等键并触发有界认领封批。
 * @status : create
 */
@Service
public class DefaultSettlementAutomaticBatchService implements SettlementAutomaticBatchService {

    private static final int GROUP_PAGE_SIZE = 100;

    private final MerchantSettlementProfileMapper profileMapper;
    private final SettlementBatchCreationService batchCreationService;
    private final SettlementCandidateBulkClaimService bulkClaimService;

    public DefaultSettlementAutomaticBatchService(MerchantSettlementProfileMapper profileMapper,
                                                  SettlementBatchCreationService batchCreationService,
                                                  SettlementCandidateBulkClaimService bulkClaimService) {
        this.profileMapper = profileMapper;
        this.batchCreationService = batchCreationService;
        this.bulkClaimService = bulkClaimService;
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
            SettlementBatchCreateResult result = batchCreationService.create(command(group, window));
            bulkClaimService.claimAndSeal(result.settlementBatchNo(), LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            processed++;
        }
        return processed;
    }

    private SettlementBatchCreateCommand command(SettlementBatchGroupDO group, BatchWindow window) {
        SettlementBatchType batchType;
        try {
            batchType = SettlementBatchType.valueOf(group.getBatchType());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("automatic settlement batch type is invalid", exception);
        }
        if (batchType != SettlementBatchType.REGULAR
                && batchType != SettlementBatchType.RESERVE_RELEASE) {
            throw new IllegalStateException("automatic settlement batch type is unsupported");
        }
        return new SettlementBatchCreateCommand(
                "AUTO:" + batchType.name() + ":" + group.getSettlementProfileId()
                        + ":" + window.businessDate() + ":" + requireAnchor(group.getAnchorCandidateId()),
                window.businessDate(),
                group.getBusinessTimeZone(),
                group.getMerchantId(),
                group.getSettlementProfileId(),
                group.getSettlementAccountId(),
                group.getTargetCurrency(),
                requireExponent(group.getTargetCurrencyExponent()),
                batchType,
                null,
                window.cutoffBeginUtc(),
                window.cutoffEndUtc());
    }

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

    private int requireExponent(Integer value) {
        if (value == null || value < 0 || value > 8) {
            throw new IllegalStateException("settlement batch target currency exponent is invalid");
        }
        return value;
    }

    private long requireAnchor(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalStateException("settlement batch anchor candidate id is invalid");
        }
        return value;
    }

    private record BatchWindow(LocalDate businessDate,
                               LocalDateTime cutoffBeginUtc,
                               LocalDateTime cutoffEndUtc) {
    }
}
