package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementCandidateClaimOutcome;
import com.scott.payment.settlement.domain.model.SettlementCandidateStatus;
import com.scott.payment.settlement.dto.SettlementCandidateClaimCommand;
import com.scott.payment.settlement.dto.SettlementCandidateClaimResult;
import com.scott.payment.settlement.entity.SettlementBatchCandidateDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.service.SettlementCandidateClaimService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementCandidateClaimService
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 默认候选认领服务，以批次首锁、依赖核验、真实候选 CAS 和不可删除关系表保证跨批独占。
 * @status : create
 */
@Service
public class DefaultSettlementCandidateClaimService implements SettlementCandidateClaimService {

    /**
     * {@code CLAIMED_RELATION_STATUS}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String CLAIMED_RELATION_STATUS = "CLAIMED";

    private final SettlementBatchMapper batchMapper;
    private final SettlementCandidateMapper candidateMapper;
    private final SettlementBatchCandidateMapper relationMapper;

    /**
     * 创建候选认领服务。
     *
     * @param batchMapper 结算批次 Mapper
     * @param candidateMapper 清分候选 Mapper
     * @param relationMapper 批次候选审计关系 Mapper
     */
    public DefaultSettlementCandidateClaimService(SettlementBatchMapper batchMapper,
                                                  SettlementCandidateMapper candidateMapper,
                                                  SettlementBatchCandidateMapper relationMapper) {
        this.batchMapper = batchMapper;
        this.candidateMapper = candidateMapper;
        this.relationMapper = relationMapper;
    }

    /**
     * 在 transaction 主库事务中独占认领单个候选；同批重复调用返回既有关系，不重复增加批次数量。
     *
     * @param command 批次、候选、预期版本和认领时间
     * @return 首次认领或同批幂等重试结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementCandidateClaimResult claim(SettlementCandidateClaimCommand command) {
        Objects.requireNonNull(command, "settlement candidate claim command is required");
        SettlementBatchDO batch = requireClaimableBatch(command.settlementBatchNo());
        SettlementCandidateDO candidate = candidateMapper.selectByIdForUpdate(command.candidateId());
        if (candidate == null) {
            throw new IllegalStateException("settlement candidate does not exist");
        }
        requireSourceCompatible(batch, candidate);

        if (SettlementCandidateStatus.CLAIMED.name().equals(candidate.getCandidateStatus())) {
            return alreadyClaimed(command, candidate);
        }
        requireReadyCandidate(command, batch, candidate);
        if (candidateMapper.countUnresolvedDependencies(
                candidate.getId(), batch.getSettlementBatchNo()) > 0) {
            throw new IllegalStateException("settlement candidate dependency is unresolved");
        }
        if (candidateMapper.claim(
                candidate.getId(), batch.getSettlementBatchNo(), batch.getSettlementProfileId(),
                command.expectedCandidateVersion(), command.claimedTime()) != 1) {
            throw new IllegalStateException("settlement candidate claim CAS failed");
        }

        SettlementBatchCandidateDO expected = relation(batch, candidate, command);
        relationMapper.insertIdempotent(expected);
        SettlementBatchCandidateDO stored = relationMapper.selectByBatchAndCandidateForUpdate(
                batch.getSettlementBatchNo(), candidate.getId());
        verifyRelation(stored, expected);
        int projectableDelta = "CLEARING_REVISION".equals(candidate.getSourceType()) ? 1 : 0;
        if (batchMapper.incrementCandidateCount(batch.getSettlementBatchNo(), projectableDelta,
                batch.getVersion()) != 1) {
            throw new IllegalStateException("settlement batch candidate count CAS failed");
        }
        return new SettlementCandidateClaimResult(
                SettlementCandidateClaimOutcome.CLAIMED,
                batch.getSettlementBatchNo(),
                candidate.getId(),
                stored.getBatchCandidateNo());
    }

    /** 锁读并校验批次仍允许追加候选且关键维度和版本完整。 */
    private SettlementBatchDO requireClaimableBatch(String settlementBatchNo) {
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(settlementBatchNo);
        if (batch == null || batch.getBatchStatus() == null || batch.getVersion() == null
                || batch.getProjectableCandidateCount() == null) {
            throw new IllegalStateException("settlement batch does not exist or has no version");
        }
        SettlementBatchStatus status;
        try {
            status = SettlementBatchStatus.valueOf(batch.getBatchStatus());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("settlement batch status is unsupported", exception);
        }
        if (!status.allowsCandidateClaim()) {
            throw new IllegalStateException("settlement batch status does not allow candidate claim");
        }
        return batch;
    }

    /** 识别候选和关系均已属于同批次的合法重放，其他部分归属一律拒绝。 */
    private SettlementCandidateClaimResult alreadyClaimed(SettlementCandidateClaimCommand command,
                                                          SettlementCandidateDO candidate) {
        if (!Objects.equals(candidate.getSettlementBatchNo(), command.settlementBatchNo())) {
            throw new IllegalStateException("settlement candidate is already claimed by another batch");
        }
        SettlementBatchCandidateDO relation = relationMapper.selectByBatchAndCandidateForUpdate(
                command.settlementBatchNo(), candidate.getId());
        if (relation == null || !CLAIMED_RELATION_STATUS.equals(relation.getRelationStatus())) {
            throw new IllegalStateException("claimed settlement candidate has no active audit relation");
        }
        return new SettlementCandidateClaimResult(
                SettlementCandidateClaimOutcome.ALREADY_CLAIMED,
                command.settlementBatchNo(),
                candidate.getId(),
                relation.getBatchCandidateNo());
    }

    /** 校验候选 READY、版本匹配、非影子且未被批次或预审占用。 */
    private void requireReadyCandidate(SettlementCandidateClaimCommand command,
                                       SettlementBatchDO batch,
                                       SettlementCandidateDO candidate) {
        if (!SettlementCandidateStatus.READY.name().equals(candidate.getCandidateStatus())
                || candidate.getSettlementBatchNo() != null) {
            throw new IllegalStateException("settlement candidate is not ready for claim");
        }
        if (!Objects.equals(candidate.getShadowMode(), 0)) {
            throw new IllegalStateException("shadow settlement candidate cannot enter a real batch");
        }
        if (!Objects.equals(candidate.getVersion(), command.expectedCandidateVersion())) {
            throw new IllegalStateException("settlement candidate expected version is stale");
        }
        boolean sameBatchDimension = Objects.equals(candidate.getMerchantId(), batch.getMerchantId())
                && Objects.equals(candidate.getSettlementProfileId(), batch.getSettlementProfileId())
                && Objects.equals(candidate.getTargetCurrency(), batch.getTargetCurrency())
                && Objects.equals(candidate.getTargetCurrencyExponent(), batch.getTargetCurrencyExponent());
        if (!sameBatchDimension) {
            throw new IllegalStateException("settlement candidate does not match batch dimensions");
        }
        if (candidate.getSettlementEligibleDate() == null || batch.getBusinessDate() == null
                || candidate.getSettlementEligibleDate().isAfter(batch.getBusinessDate())) {
            throw new IllegalStateException("settlement candidate is not eligible on batch business date");
        }
    }

    /** 校验 REGULAR、RESERVE_RELEASE 或 ADJUSTMENT 批次只认领对应来源候选。 */
    private void requireSourceCompatible(SettlementBatchDO batch, SettlementCandidateDO candidate) {
        boolean compatible = switch (batch.getBatchType() == null ? "" : batch.getBatchType()) {
            case "REGULAR" -> "CLEARING_REVISION".equals(candidate.getSourceType());
            case "RESERVE_RELEASE" -> "RESERVE_RELEASE".equals(candidate.getSourceType());
            case "ADJUSTMENT" -> "ADJUSTMENT".equals(candidate.getSourceType());
            default -> false;
        };
        if (!compatible) {
            throw new IllegalStateException("settlement candidate source does not match batch type");
        }
    }

    /** 构造批次候选不可删除关系并冻结来源身份。 */
    private SettlementBatchCandidateDO relation(SettlementBatchDO batch,
                                                SettlementCandidateDO candidate,
                                                SettlementCandidateClaimCommand command) {
        SettlementBatchCandidateDO row = new SettlementBatchCandidateDO();
        row.setBatchCandidateNo(relationNo(batch.getSettlementBatchNo(), candidate.getId()));
        row.setSettlementBatchNo(batch.getSettlementBatchNo());
        row.setCandidateId(candidate.getId());
        row.setSourceType(candidate.getSourceType());
        row.setSourceBusinessId(candidate.getSourceBusinessId());
        row.setSourceRevision(candidate.getSourceRevision());
        row.setRelationStatus(CLAIMED_RELATION_STATUS);
        row.setClaimedTime(command.claimedTime());
        row.setVersion(0L);
        row.setCreateTime(command.claimedTime());
        row.setUpdateTime(command.claimedTime());
        return row;
    }

    /** 对关系唯一键重放核对批次、候选和来源身份，拒绝碰撞。 */
    private void verifyRelation(SettlementBatchCandidateDO actual, SettlementBatchCandidateDO expected) {
        boolean matches = actual != null
                && Objects.equals(actual.getBatchCandidateNo(), expected.getBatchCandidateNo())
                && Objects.equals(actual.getSettlementBatchNo(), expected.getSettlementBatchNo())
                && Objects.equals(actual.getCandidateId(), expected.getCandidateId())
                && Objects.equals(actual.getSourceType(), expected.getSourceType())
                && Objects.equals(actual.getSourceBusinessId(), expected.getSourceBusinessId())
                && Objects.equals(actual.getSourceRevision(), expected.getSourceRevision());
        if (!matches) {
            throw new IllegalStateException("settlement batch candidate unique key contains mismatched identity");
        }
    }

    /**
     * 使用批次号和候选主键生成可重放的稳定关系号，避免重试产生新审计行。
     */
    private String relationNo(String settlementBatchNo, Long candidateId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((settlementBatchNo + "|" + candidateId).getBytes(StandardCharsets.UTF_8));
            return "BC" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
