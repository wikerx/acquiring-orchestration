package com.scott.payment.risk.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary.TransitionOutcome;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.mapper.MerchantLimitReservationMapper;
import com.scott.payment.risk.service.MerchantLimitReservationStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 基于数据库唯一键和版本号 CAS 的商户累计限额预占状态服务。
 */
@Service
public class DefaultMerchantLimitReservationStateService implements MerchantLimitReservationStateService {

    /** 新建预占事实的乐观锁初始版本。 */
    private static final int INITIAL_VERSION = 0;

    /** 新建预占事实的未删除标记。 */
    private static final int NOT_DELETED = 0;

    /** 预占事实数据访问组件，负责唯一键查询、行锁和 CAS 状态更新。 */
    private final MerchantLimitReservationMapper mapper;

    /** 统一生成状态迁移时间的时钟，测试场景可注入固定时间。 */
    private final Clock clock;

    /**
     * 使用系统默认时区时钟创建状态服务。
     *
     * @param mapper 预占事实数据访问组件
     */
    @Autowired
    public DefaultMerchantLimitReservationStateService(MerchantLimitReservationMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    DefaultMerchantLimitReservationStateService(MerchantLimitReservationMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 在独立事务中提交 PREPARING 意图，确保 Redis 变更前已有可恢复记录。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public MerchantLimitReservationDO prepare(MerchantLimitReservationDO candidate) {
        validateCandidate(candidate);
        LocalDateTime now = LocalDateTime.now(clock);
        candidate.setReservationStatus(MerchantLimitReservationStatus.PREPARING.name());
        candidate.setVersion(INITIAL_VERSION);
        candidate.setDeleted(NOT_DELETED);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        try {
            if (mapper.insertPreparing(candidate) != 1) {
                throw new IllegalStateException("merchant limit reservation prepare insert failed");
            }
            return candidate;
        } catch (DuplicateKeyException exception) {
            MerchantLimitReservationDO existing = mapper.selectByBusinessKey(
                    candidate.getTransactionId(),
                    candidate.getRuleId(),
                    candidate.getLimitType(),
                    candidate.getPeriodBucket());
            if (existing == null) {
                throw exception;
            }
            requireSameReservation(existing, candidate);
            return existing;
        }
    }

    /**
     * 在独立事务中将 PREPARING 记录迁移为 RESERVED，CAS 冲突后按最新状态判定幂等结果。
     *
     * @param reservation 已完成 Redis 原子预占的持久化记录
     * @return 已处于 RESERVED/CONFIRMED 或本次迁移成功时返回 {@code true}
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markReserved(MerchantLimitReservationDO reservation) {
        if (reservation == null || reservation.getId() == null || reservation.getVersion() == null) {
            return false;
        }
        MerchantLimitReservationStatus status = statusOf(reservation);
        if (status == MerchantLimitReservationStatus.RESERVED
                || status == MerchantLimitReservationStatus.CONFIRMED) {
            return true;
        }
        if (status != MerchantLimitReservationStatus.PREPARING) {
            return false;
        }
        int updated = mapper.transitionStatus(
                reservation.getId(),
                reservation.getVersion(),
                MerchantLimitReservationStatus.PREPARING.name(),
                MerchantLimitReservationStatus.RESERVED.name(),
                null,
                LocalDateTime.now(clock));
        if (updated == 1) {
            return true;
        }
        MerchantLimitReservationDO latest =
                mapper.selectReservationById(reservation.getId());
        MerchantLimitReservationStatus latestStatus = statusOf(latest);
        return latestStatus == MerchantLimitReservationStatus.RESERVED
                || latestStatus == MerchantLimitReservationStatus.CONFIRMED;
    }

    /**
     * 在生命周期编排事务中确认指定交易的全部 RESERVED 记录，CONFIRMED 终态不可逆。
     *
     * <p>存在外层事务时必须复用同一连接，避免小连接池下多个成功事件同时以
     * REQUIRES_NEW 等待额外连接形成池级死锁；独立调用时 REQUIRED 仍会创建事务。</p>
     *
     * @param transactionId 平台交易号
     * @return 实际迁移、幂等命中和冲突数量汇总
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public MerchantLimitReservationTransitionSummary confirm(String transactionId) {
        return transitionAll(findByTransactionId(transactionId), MerchantLimitReservationStatus.CONFIRMED, null);
    }

    /**
     * 在独立事务中取消指定交易的 PREPARING/RESERVED 记录并保护既有终态。
     *
     * @param transactionId 平台交易号
     * @param reason 不含敏感请求数据的取消原因，持久化前最多保留 256 个字符
     * @return 实际迁移、幂等命中和冲突数量汇总
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public MerchantLimitReservationTransitionSummary cancel(String transactionId, String reason) {
        return transitionAll(findByTransactionId(transactionId), MerchantLimitReservationStatus.CANCELLED, reason);
    }

    /**
     * 在独立事务中取消指定预占集合，非法源状态和相反终态按冲突统计。
     *
     * @param reservations 待取消的持久化预占记录
     * @param reason 不含敏感请求数据的取消原因，持久化前最多保留 256 个字符
     * @return 实际迁移、幂等命中和冲突数量汇总
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public MerchantLimitReservationTransitionSummary cancel(List<MerchantLimitReservationDO> reservations,
                                                             String reason) {
        return transitionAll(reservations, MerchantLimitReservationStatus.CANCELLED, reason);
    }

    /**
     * 在调用方现有事务中锁定指定交易的预占事实，避免并发确认覆盖取消。
     *
     * @param transactionId 平台交易号
     * @return 已加数据库行锁的预占记录；参数为空或记录不存在时返回空集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<MerchantLimitReservationDO> lockByTransactionId(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            return List.of();
        }
        List<MerchantLimitReservationDO> reservations =
                mapper.selectByTransactionIdForUpdate(transactionId.trim());
        return reservations == null ? List.of() : reservations;
    }

    /**
     * 取消调用方已持有行锁的预占记录，必须在现有事务中执行。
     *
     * @param reservations 已在当前事务中加锁的预占记录
     * @param reason 不含敏感请求数据的取消原因
     * @return 实际迁移、幂等命中和冲突数量汇总
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public MerchantLimitReservationTransitionSummary cancelLocked(
            List<MerchantLimitReservationDO> reservations,
            String reason) {
        return transitionAll(
                reservations,
                MerchantLimitReservationStatus.CANCELLED,
                reason);
    }

    /**
     * 查询指定交易的全部预占事实，不获取数据库行锁。
     *
     * @param transactionId 平台交易号
     * @return 按主键升序返回的预占记录；参数为空或记录不存在时返回空集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    public List<MerchantLimitReservationDO> findByTransactionId(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            return List.of();
        }
        List<MerchantLimitReservationDO> reservations = mapper.selectByTransactionId(transactionId.trim());
        return reservations == null ? List.of() : reservations;
    }

    /**
     * 查询更新时间早于阈值的非终态预占，供补偿任务核对 Redis 与数据库事实。
     *
     * @param updatedBefore 最晚更新时间阈值
     * @param limit 单批最大返回数，必须为正数
     * @return 待核对的 PREPARING/RESERVED 记录；参数无效时返回空集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    public List<MerchantLimitReservationDO> findStaleNonTerminal(LocalDateTime updatedBefore, int limit) {
        if (updatedBefore == null || limit <= 0) {
            return List.of();
        }
        List<MerchantLimitReservationDO> reservations = mapper.selectStaleNonTerminal(updatedBefore, limit);
        return reservations == null ? List.of() : reservations;
    }

    /**
     * 逐条执行目标状态迁移并累计结果，不因单条冲突覆盖其他记录的处理结果。
     *
     * @param reservations 待迁移的预占记录
     * @param target 目标状态，仅支持 CONFIRMED 或 CANCELLED
     * @param reason 取消原因，确认场景为 {@code null}
     * @return 实际迁移、幂等命中和冲突数量汇总
     */
    private MerchantLimitReservationTransitionSummary transitionAll(List<MerchantLimitReservationDO> reservations,
                                                                    MerchantLimitReservationStatus target,
                                                                    String reason) {
        MerchantLimitReservationTransitionSummary summary = MerchantLimitReservationTransitionSummary.empty();
        if (reservations == null) {
            return summary;
        }
        for (MerchantLimitReservationDO reservation : reservations) {
            summary = summary.plus(transition(reservation, target, reason));
        }
        return summary;
    }

    /**
     * 使用版本号乐观锁推进单条预占；并发方已完成相同目标时返回幂等结果。
     */
    private TransitionOutcome transition(MerchantLimitReservationDO reservation,
                                         MerchantLimitReservationStatus target,
                                         String reason) {
        if (reservation == null || reservation.getId() == null || reservation.getVersion() == null) {
            return TransitionOutcome.CONFLICTED;
        }
        MerchantLimitReservationStatus current = statusOf(reservation);
        if (current == target) {
            return TransitionOutcome.IDEMPOTENT;
        }
        if (!canTransition(current, target)) {
            return TransitionOutcome.CONFLICTED;
        }
        int updated = mapper.transitionStatus(
                reservation.getId(),
                reservation.getVersion(),
                current.name(),
                target.name(),
                safeReason(reason),
                LocalDateTime.now(clock));
        if (updated == 1) {
            return TransitionOutcome.APPLIED;
        }
        MerchantLimitReservationDO latest = mapper.selectReservationById(reservation.getId());
        if (latest != null && statusOf(latest) == target) {
            return TransitionOutcome.IDEMPOTENT;
        }
        return TransitionOutcome.CONFLICTED;
    }

    /**
     * 限定预占状态机：仅 RESERVED 可确认，PREPARING/RESERVED 可取消，终态不可逆。
     */
    private boolean canTransition(MerchantLimitReservationStatus current,
                                  MerchantLimitReservationStatus target) {
        if (current == null || current.isTerminal()) {
            return false;
        }
        if (target == MerchantLimitReservationStatus.CONFIRMED) {
            return current == MerchantLimitReservationStatus.RESERVED;
        }
        return target == MerchantLimitReservationStatus.CANCELLED
                && (current == MerchantLimitReservationStatus.PREPARING
                || current == MerchantLimitReservationStatus.RESERVED);
    }

    /** 安全解析持久化状态，空值或未知编码返回 null 并交由调用方保守处理。 */
    private MerchantLimitReservationStatus statusOf(MerchantLimitReservationDO reservation) {
        if (reservation == null || !StringUtils.hasText(reservation.getReservationStatus())) {
            return null;
        }
        try {
            return MerchantLimitReservationStatus.valueOf(reservation.getReservationStatus().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 校验预占事实记录的完整性；amountUnits 必须为正且以六位小数整数单位保存。
     */
    private void validateCandidate(MerchantLimitReservationDO candidate) {
        if (candidate == null
                || !StringUtils.hasText(candidate.getTransactionId())
                || !StringUtils.hasText(candidate.getRiskRecordNo())
                || !StringUtils.hasText(candidate.getMerchantId())
                || candidate.getRuleId() == null
                || !StringUtils.hasText(candidate.getLimitType())
                || !StringUtils.hasText(candidate.getCurrency())
                || !StringUtils.hasText(candidate.getPeriodBucket())
                || candidate.getPeriodBeginTime() == null
                || candidate.getPeriodEndTime() == null
                || candidate.getAmountUnits() == null
                || candidate.getAmountUnits() <= 0
                || !StringUtils.hasText(candidate.getCounterMode())
                || candidate.getExpiresAt() == null) {
            throw new IllegalArgumentException("merchant limit reservation candidate is invalid");
        }
    }

    /**
     * 校验同一交易的重复预占载荷完全一致，防止幂等键复用污染金额或周期。
     */
    private void requireSameReservation(MerchantLimitReservationDO existing,
                                        MerchantLimitReservationDO candidate) {
        boolean same = Objects.equals(existing.getMerchantId(), candidate.getMerchantId())
                && Objects.equals(existing.getCurrency(), candidate.getCurrency())
                && Objects.equals(existing.getPeriodBeginTime(), candidate.getPeriodBeginTime())
                && Objects.equals(existing.getPeriodEndTime(), candidate.getPeriodEndTime())
                && Objects.equals(existing.getAmountUnits(), candidate.getAmountUnits())
                && Objects.equals(existing.getCounterMode(), candidate.getCounterMode());
        if (!same) {
            throw new IllegalStateException("merchant limit reservation idempotency payload mismatch");
        }
    }

    /** 将取消原因限制在 256 个字符内，避免异常文本无限写入审计字段。 */
    private String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }
}
