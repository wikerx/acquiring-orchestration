package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingSettlementEligibilityMapper;
import com.scott.payment.clearing.service.SettlementEligibilityService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.SettlementPolicySnapshot;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 默认交易结算周期计算；历史 v3 快照保持原有交易日立即可结算语义。 */
@Service
public class DefaultSettlementEligibilityService implements SettlementEligibilityService {

    private final ClearingSettlementEligibilityMapper mapper;

    public DefaultSettlementEligibilityService(ClearingSettlementEligibilityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @DS(DataSourceName.TRANSACTION)
    public LocalDate calculate(String merchantId, LocalDate transactionDate, FeeVersionSnapshot snapshot) {
        Objects.requireNonNull(transactionDate, "transaction date is required");
        Objects.requireNonNull(snapshot, "fee snapshot is required");
        if (merchantId == null || merchantId.isBlank() || !merchantId.equals(snapshot.merchantId())) {
            throw new IllegalArgumentException("settlement eligibility merchant identity is invalid");
        }
        if (snapshot.schemaVersion() == 3) {
            return transactionDate;
        }
        SettlementPolicySnapshot policy = snapshot.settlementPolicy();
        boolean initialCycle = mapper.countPostedRegularSettlement(merchantId) == 0;
        int delayDays = initialCycle ? policy.initialDelayDays() : policy.regularDelayDays();
        LocalDate delayed = "D".equals(policy.delayUnit())
                ? transactionDate.plusDays(delayDays)
                : addConfirmedWorkdays(transactionDate, delayDays);
        return alignFrequency(delayed, policy);
    }

    private LocalDate addConfirmedWorkdays(LocalDate startDate, int days) {
        LocalDate result = mapper.selectNthConfirmedWorkday(startDate, days - 1);
        if (result == null || mapper.countConfirmedCalendarDays(startDate, result)
                != ChronoUnit.DAYS.between(startDate, result)) {
            throw new ClearingProcessingException(ClearingFailureCodeEnum.SETTLEMENT_CALENDAR_UNAVAILABLE,
                    "confirmed settlement calendar does not cover the required workday range");
        }
        return result;
    }

    private LocalDate alignFrequency(LocalDate date, SettlementPolicySnapshot policy) {
        return switch (policy.settlementFrequency()) {
            case "DAILY" -> date;
            case "WEEKLY" -> alignWeekly(date, policy.frequencyDay());
            case "BIWEEKLY" -> alignBiweekly(date, policy);
            case "MONTHLY" -> alignMonthly(date, policy.frequencyDay());
            default -> throw new IllegalArgumentException("unsupported settlement frequency");
        };
    }

    private LocalDate alignWeekly(LocalDate date, int isoDay) {
        int delta = Math.floorMod(isoDay - date.getDayOfWeek().getValue(), 7);
        return date.plusDays(delta);
    }

    private LocalDate alignBiweekly(LocalDate date, SettlementPolicySnapshot policy) {
        LocalDate anchor = alignWeekly(policy.frequencyAnchorDate(), policy.frequencyDay());
        if (!anchor.isBefore(date)) {
            return anchor;
        }
        long days = ChronoUnit.DAYS.between(anchor, date);
        long periods = (days + 13) / 14;
        return anchor.plusDays(periods * 14);
    }

    private LocalDate alignMonthly(LocalDate date, int monthDay) {
        LocalDate current = date.withDayOfMonth(monthDay);
        return current.isBefore(date) ? date.plusMonths(1).withDayOfMonth(monthDay) : current;
    }
}
