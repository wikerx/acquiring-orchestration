package com.scott.payment.risk.application;

import com.scott.payment.risk.api.internal.dto.MerchantLimitReservationCommandDTO;
import com.scott.payment.risk.api.internal.dto.MerchantLimitReservationCommandResultDTO;
import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.service.MerchantLimitReservationLifecycleCoordinator;
import org.springframework.stereotype.Service;

/**
 * 商户累计限额预占内部用例编排。
 */
@Service
public class MerchantLimitReservationApplicationService {

    /**
     * 累计限额预占生命周期协调器，负责数据库状态与 Redis 回滚的有序协作。
     */
    private final MerchantLimitReservationLifecycleCoordinator coordinator;

    /**
     * 创建累计限额预占内部用例服务。
     *
     * @param coordinator 生命周期协调器
     */
    public MerchantLimitReservationApplicationService(
            MerchantLimitReservationLifecycleCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * 按平台交易号取消全部可取消预占。
     * <p>
     * 同一交易重复取消按幂等统计；已确认或发生并发冲突的记录不会被强制覆盖。
     * </p>
     *
     * @param commandDTO 交易号和审计原因
     * @return 应用、幂等和冲突记录数
     */
    public MerchantLimitReservationCommandResultDTO cancel(
            MerchantLimitReservationCommandDTO commandDTO) {
        MerchantLimitReservationTransitionSummary summary = coordinator.cancel(
                commandDTO.getTransactionId(),
                commandDTO.getReason());
        MerchantLimitReservationCommandResultDTO result = new MerchantLimitReservationCommandResultDTO();
        result.setApplied(summary.applied());
        result.setIdempotent(summary.idempotent());
        result.setConflicted(summary.conflicted());
        return result;
    }
}
