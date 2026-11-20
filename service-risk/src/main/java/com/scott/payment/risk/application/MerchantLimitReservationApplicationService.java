package com.scott.payment.risk.application;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.risk.api.internal.dto.MerchantLimitReservationCommandDTO;
import com.scott.payment.risk.api.internal.dto.MerchantLimitReservationCommandResultDTO;
import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.domain.PaymentPreparationReservationScope;
import com.scott.payment.risk.service.MerchantLimitReservationLifecycleCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

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
        MerchantLimitReservationTransitionSummary summary = coordinator.cancelCompensation(
                commandDTO.getTransactionId(),
                preparationScope(commandDTO),
                commandDTO.getReason());
        MerchantLimitReservationCommandResultDTO result = new MerchantLimitReservationCommandResultDTO();
        result.setApplied(summary.applied());
        result.setIdempotent(summary.idempotent());
        result.setConflicted(summary.conflicted());
        return result;
    }

    private PaymentPreparationReservationScope preparationScope(MerchantLimitReservationCommandDTO command) {
        String merchant = command.getMerchantId();
        String key = command.getPaymentPreparationKey();
        Long generation = command.getPaymentPreparationGeneration();
        String time = command.getPaymentTransactionDateTime();
        if (merchant == null && key == null && generation == null && time == null) {
            return null;
        }
        if (!StringUtils.hasText(merchant) || key == null || !key.matches("[0-9a-f]{64}")
                || generation == null || generation < 1 || !StringUtils.hasText(time)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        try {
            return new PaymentPreparationReservationScope(merchant, key, generation, LocalDateTime.parse(time));
        } catch (DateTimeParseException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }
}
