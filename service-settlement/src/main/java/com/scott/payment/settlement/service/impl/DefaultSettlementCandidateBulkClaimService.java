package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.entity.SettlementBatchCandidateDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.service.SettlementCandidateBulkClaimService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementCandidateBulkClaimService
 * @date : 2026-08-26 22:20
 * @email : scott_x@163.com
 * @description : 批次首锁后按依赖拓扑分页锁定候选，以版本批量 CAS、批量审计关系和批次计数 CAS 完成认领封批。
 * @status : create
 */
@Service
public class DefaultSettlementCandidateBulkClaimService implements SettlementCandidateBulkClaimService {

    /**
     * {@code CLAIM_PAGE_SIZE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int CLAIM_PAGE_SIZE = 200;
    /** 单批硬上限 1000 条，避免事实读取和结果入库随历史积压无界增长。 */
    private static final int MAX_CLAIM_PAGES_PER_BATCH = 5;

    private final SettlementBatchMapper batchMapper;
    private final SettlementCandidateMapper candidateMapper;
    private final SettlementBatchCandidateMapper relationMapper;

    public DefaultSettlementCandidateBulkClaimService(SettlementBatchMapper batchMapper,
                                                      SettlementCandidateMapper candidateMapper,
                                                      SettlementBatchCandidateMapper relationMapper) {
        this.batchMapper = batchMapper;
        this.candidateMapper = candidateMapper;
        this.relationMapper = relationMapper;
    }

    /**
     * 单批最多认领 1000 个候选；达到上限后立即封批，剩余 READY 候选由下一稳定锚点创建新批。
     *
     * @param settlementBatchNo 目标批次号
     * @param claimedTime 统一认领时间
     * @return 批次封存后的候选总数；重复调用已封存批次时返回既有总数
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int claimAndSeal(String settlementBatchNo, LocalDateTime claimedTime) {
        if (settlementBatchNo == null || settlementBatchNo.isBlank()) {
            throw new IllegalArgumentException("settlement batch number is required");
        }
        Objects.requireNonNull(claimedTime, "settlement bulk claim time is required");
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(settlementBatchNo.trim());
        if (batch == null || batch.getVersion() == null || batch.getCandidateCount() == null
                || batch.getProjectableCandidateCount() == null) {
            throw new IllegalStateException("settlement batch does not exist or is incomplete");
        }
        SettlementBatchStatus status = parseStatus(batch.getBatchStatus());
        if (status == SettlementBatchStatus.CLAIMED) {
            return batch.getCandidateCount();
        }
        if (!status.allowsCandidateClaim()) {
            throw new IllegalStateException("settlement batch status does not allow bulk claim");
        }

        for (int page = 0; page < MAX_CLAIM_PAGES_PER_BATCH; page++) {
            List<SettlementCandidateDO> candidates = candidateMapper.selectClaimableByBatchForUpdate(
                    batch.getSettlementBatchNo(), CLAIM_PAGE_SIZE);
            if (candidates.isEmpty()) {
                sealIfNonEmpty(batch, claimedTime);
                return batch.getCandidateCount();
            }
            claimPage(batch, candidates, claimedTime);
        }
        sealIfNonEmpty(batch, claimedTime);
        return batch.getCandidateCount();
    }

    /** 对一页 READY 候选执行批量版本 CAS、关系幂等插入和批次候选计数 CAS。 */
    private void claimPage(SettlementBatchDO batch,
                           List<SettlementCandidateDO> candidates,
                           LocalDateTime claimedTime) {
        int affected = candidateMapper.claimBatch(candidates, batch.getSettlementBatchNo(),
                batch.getSettlementProfileId(), claimedTime);
        if (affected != candidates.size()) {
            throw new IllegalStateException("settlement candidate bulk claim CAS affected an unexpected row count");
        }
        List<SettlementBatchCandidateDO> relations = new ArrayList<>(candidates.size());
        for (SettlementCandidateDO candidate : candidates) {
            relations.add(relation(batch.getSettlementBatchNo(), candidate, claimedTime));
        }
        if (relationMapper.insertBatchIdempotent(relations) != relations.size()) {
            throw new IllegalStateException("settlement batch candidate relation insert affected an unexpected row count");
        }
        int projectableDelta = (int) candidates.stream()
                .filter(candidate -> "CLEARING_REVISION".equals(candidate.getSourceType()))
                .count();
        if (batchMapper.incrementCandidateCountBy(batch.getSettlementBatchNo(), candidates.size(),
                projectableDelta, batch.getVersion()) != 1) {
            throw new IllegalStateException("settlement batch bulk candidate count CAS failed");
        }
        batch.setCandidateCount(batch.getCandidateCount() + candidates.size());
        batch.setProjectableCandidateCount(batch.getProjectableCandidateCount() + projectableDelta);
        batch.setVersion(batch.getVersion() + 1);
        batch.setBatchStatus(SettlementBatchStatus.CLAIMING.name());
    }

    /** 仅对非空批次从 CREATED 原子封存为 CLAIMED，0 候选批次由上层取消留痕。 */
    private void sealIfNonEmpty(SettlementBatchDO batch, LocalDateTime sealedTime) {
        if (batch.getCandidateCount() == 0) {
            return;
        }
        if (batchMapper.sealClaimedBatch(batch.getSettlementBatchNo(), batch.getCandidateCount(),
                batch.getVersion(), sealedTime) != 1) {
            throw new IllegalStateException("settlement batch seal CAS failed");
        }
    }

    /** 构造批次候选不可删除关系，冻结来源类型、业务 ID 和修订号。 */
    private SettlementBatchCandidateDO relation(String batchNo,
                                                SettlementCandidateDO candidate,
                                                LocalDateTime claimedTime) {
        SettlementBatchCandidateDO row = new SettlementBatchCandidateDO();
        row.setBatchCandidateNo(relationNo(batchNo, candidate.getId()));
        row.setSettlementBatchNo(batchNo);
        row.setCandidateId(candidate.getId());
        row.setSourceType(candidate.getSourceType());
        row.setSourceBusinessId(candidate.getSourceBusinessId());
        row.setSourceRevision(candidate.getSourceRevision());
        row.setRelationStatus("CLAIMED");
        row.setClaimedTime(claimedTime);
        row.setVersion(0L);
        row.setCreateTime(claimedTime);
        row.setUpdateTime(claimedTime);
        return row;
    }

    /** 将批次状态字符串解析为枚举，拒绝未知值进入认领状态机。 */
    private SettlementBatchStatus parseStatus(String value) {
        try {
            return SettlementBatchStatus.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("settlement batch status is unsupported", exception);
        }
    }

    /** 由批次号和候选主键生成稳定关系号，保证分页重放身份一致。 */
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
