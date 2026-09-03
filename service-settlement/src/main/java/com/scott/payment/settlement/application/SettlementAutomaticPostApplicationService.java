package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.dto.SettlementCommandAudit;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.service.SettlementCandidateBulkClaimService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementAutomaticPostApplicationService
 * @date : 2026-09-02 18:30
 * @email : scott_x@163.com
 * @description : 在 transaction 主库单事务内锁定自动结算锚点，并编排正式批次创建、候选认领与空批次审计。
 * @status : create
 */
@Service
public class SettlementAutomaticPostApplicationService {

    /** 结算候选主库 Mapper；用于在正式建批前锁定真实 READY 锚点，字段不含敏感信息。 */
    private final SettlementCandidateMapper candidateMapper;
    /** 结算批次创建领域服务；由数据库请求唯一键和日序列保证批次身份幂等。 */
    private final SettlementBatchCreationService batchCreationService;
    /** 批量候选认领服务；在同一 transaction 主库事务中执行候选 CAS 和封批。 */
    private final SettlementCandidateBulkClaimService bulkClaimService;
    /** 批次状态命令编排；仅用于极端空认领兜底取消并记录系统操作审计。 */
    private final SettlementBatchCommandApplicationService batchCommandService;

    /**
     * 创建自动正式结算事务编排器。
     *
     * @param candidateMapper 候选锁定 Mapper
     * @param batchCreationService 批次幂等创建服务
     * @param bulkClaimService 候选批量认领与封批服务
     * @param batchCommandService 批次取消状态机编排服务
     */
    public SettlementAutomaticPostApplicationService(SettlementCandidateMapper candidateMapper,
                                                     SettlementBatchCreationService batchCreationService,
                                                     SettlementCandidateBulkClaimService bulkClaimService,
                                                     SettlementBatchCommandApplicationService batchCommandService) {
        this.candidateMapper = candidateMapper;
        this.batchCreationService = batchCreationService;
        this.bulkClaimService = bulkClaimService;
        this.batchCommandService = batchCommandService;
    }

    /**
     * 锁定当前日切窗口内第一条真实候选；空结果表示当前分组尚无可创建正式批次的事实基础。
     *
     * @param group 自动扫描得到的冻结结算维度
     * @param batchType 候选来源对应的正式批次类型
     * @param businessDate 已成熟业务日
     * @param cutoffBeginTime 日切窗口开始时间，UTC
     * @param cutoffEndTime 日切窗口结束时间，UTC
     * @param operationTime 本次调度统一操作时间，UTC
     * @return 已创建或复用并进入认领流程时返回 true；无真实候选时返回 false
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean createAndClaim(SettlementBatchGroupDO group,
                                  SettlementBatchType batchType,
                                  LocalDate businessDate,
                                  LocalDateTime cutoffBeginTime,
                                  LocalDateTime cutoffEndTime,
                                  LocalDateTime operationTime) {
        Objects.requireNonNull(group, "settlement automatic post group is required");
        Objects.requireNonNull(batchType, "settlement automatic post batch type is required");
        Objects.requireNonNull(businessDate, "settlement automatic post business date is required");
        Objects.requireNonNull(cutoffBeginTime, "settlement automatic post cutoff begin time is required");
        Objects.requireNonNull(cutoffEndTime, "settlement automatic post cutoff end time is required");
        Objects.requireNonNull(operationTime, "settlement automatic post operation time is required");
        SettlementCandidateDO anchor = candidateMapper.selectAutomaticPostAnchorForUpdate(
                group.getMerchantId(), group.getSettlementProfileId(), group.getSettlementAccountId(),
                group.getTargetCurrency(), group.getTargetCurrencyExponent(), batchType.name(),
                businessDate, cutoffEndTime);
        if (anchor == null) {
            return false;
        }
        long anchorId = requireAnchor(anchor.getId());
        long anchorVersion = requireVersion(anchor.getVersion());
        SettlementBatchCreateResult result = batchCreationService.create(new SettlementBatchCreateCommand(
                requestKey(batchType, group.getSettlementProfileId(), businessDate, anchorId, anchorVersion),
                businessDate,
                group.getBusinessTimeZone(),
                group.getMerchantId(),
                group.getSettlementProfileId(),
                group.getSettlementAccountId(),
                group.getTargetCurrency(),
                requireExponent(group.getTargetCurrencyExponent()),
                batchType,
                null,
                cutoffBeginTime,
                cutoffEndTime));
        int claimedCount = bulkClaimService.claimAndSeal(result.settlementBatchNo(), operationTime);
        if (claimedCount > 0) {
            return true;
        }
        SettlementOperatorSnapshot operator = new SettlementOperatorSnapshot(
                0L, "service-settlement", "SYSTEM", "127.0.0.1",
                "service-settlement-scheduler", operationTime);
        batchCommandService.cancelBeforePosting(
                result.settlementBatchNo(), 0L,
                new SettlementCommandAudit(
                        "AUTO_EMPTY:" + result.settlementBatchNo(),
                        "automatic batch cancelled because no candidates remained claimable",
                        operator),
                operationTime);
        return false;
    }

    /**
     * 组合批次类型、冻结档案、业务日、真实候选主键和 CAS 版本生成稳定建批幂等键。
     * 候选被取消释放时 version 会递增，因此后续合法重建不会复用已取消或已处理的历史批次。
     *
     * @param batchType 正式批次类型
     * @param settlementProfileId 冻结结算档案主键
     * @param businessDate 结算业务日
     * @param anchorId 已锁定候选主键
     * @param anchorVersion 已锁定候选 CAS 版本
     * @return 长度受控且可由数据库唯一键兜底的自动建批请求键
     */
    private String requestKey(SettlementBatchType batchType,
                              Long settlementProfileId,
                              LocalDate businessDate,
                              long anchorId,
                              long anchorVersion) {
        if (settlementProfileId == null || settlementProfileId <= 0) {
            throw new IllegalStateException("settlement automatic post profile id is invalid");
        }
        return "AUTO:" + batchType.name() + ":" + settlementProfileId
                + ":" + businessDate + ":" + anchorId + ":V" + anchorVersion;
    }

    /** 要求自动正式批次锚点候选主键有效。 */
    private long requireAnchor(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalStateException("settlement automatic post anchor candidate id is invalid");
        }
        return value;
    }

    /** 要求自动正式批次锚点携带非负 CAS 版本。 */
    private long requireVersion(Long value) {
        if (value == null || value < 0) {
            throw new IllegalStateException("settlement automatic post anchor candidate version is invalid");
        }
        return value;
    }

    /** 校验目标币种 exponent 在平台支持范围内。 */
    private int requireExponent(Integer value) {
        if (value == null || value < 0 || value > 8) {
            throw new IllegalStateException("settlement automatic post target currency exponent is invalid");
        }
        return value;
    }
}
