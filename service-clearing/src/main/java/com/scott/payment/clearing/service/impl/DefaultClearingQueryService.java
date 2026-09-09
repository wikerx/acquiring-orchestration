package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordDetailResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSummary;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingReserveLine;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingTransactionLine;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingTransactionDetailDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionDetailMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.service.ClearingQueryService;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingQueryService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 默认清分管理查询实现，只读取当前权威修订，不跨季度广播。
 * @status : update
 */
@Service
public class DefaultClearingQueryService implements ClearingQueryService {

    /**
     * {@code DEFAULT_LIMIT}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_LIMIT = 200;
    /**
     * {@code MAX_LIMIT}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int MAX_LIMIT = 1000;

    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    private final ClearingTransactionDetailMapper detailMapper;
    private final ClearingReserveMapper reserveMapper;

    public DefaultClearingQueryService(ClearingTransactionFinanceStateMapper financeStateMapper,
                                       ClearingTransactionDetailMapper detailMapper,
                                       ClearingReserveMapper reserveMapper) {
        this.financeStateMapper = financeStateMapper;
        this.detailMapper = detailMapper;
        this.reserveMapper = reserveMapper;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(readOnly = true)
    public ClearingRecordSearchResponse search(ClearingRecordSearchRequest request) {
        validateSearchRequest(request);
        int limit = request.getLimit() == null ? DEFAULT_LIMIT : request.getLimit();
        List<ClearingTransactionFinanceStateDO> rows = financeStateMapper.selectForManagementSearch(
                trim(request.getMerchantId()), trim(request.getTransactionId()), normalizedStatus(request),
                request.getBeginTime(), request.getEndTime(), request.getCursorTransactionDateTime(),
                request.getCursorId(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ClearingTransactionFinanceStateDO> page = hasMore ? rows.subList(0, limit) : rows;
        ClearingRecordSearchResponse response = new ClearingRecordSearchResponse();
        response.setRecords(page.stream().map(this::toSummary).toList());
        response.setHasMore(hasMore);
        if (!page.isEmpty()) {
            ClearingTransactionFinanceStateDO last = page.get(page.size() - 1);
            response.setNextCursorTransactionDateTime(last.getTransactionDateTime());
            response.setNextCursorId(last.getId());
        }
        return response;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(readOnly = true)
    public ClearingRecordDetailResponse detail(String transactionId, LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            throw new IllegalArgumentException("clearing detail transactionId and transactionDateTime are required");
        }
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectByTransaction(
                transactionId, transactionDateTime);
        if (state == null) {
            throw new IllegalArgumentException("clearing record does not exist");
        }
        int revision = state.getClearingRevision() == null ? 0 : state.getClearingRevision();
        ClearingRecordDetailResponse response = new ClearingRecordDetailResponse();
        response.setSummary(toSummary(state));
        if (revision < 1) {
            return response;
        }
        response.setTransactionDetails(detailMapper.selectActiveRevision(transactionId, transactionDateTime, revision)
                .stream().map(this::toTransactionLine).toList());
        response.setReserveDetails(reserveMapper.selectActiveRevision(transactionId, transactionDateTime, revision)
                .stream().map(this::toReserveLine).toList());
        return response;
    }

    private ClearingRecordSummary toSummary(ClearingTransactionFinanceStateDO row) {
        ClearingRecordSummary result = new ClearingRecordSummary();
        result.setId(row.getId());
        result.setFinanceStateId(row.getFinanceStateId());
        result.setTransactionId(row.getTransactionId());
        result.setOperationId(row.getOperationId());
        result.setMerchantId(row.getMerchantId());
        result.setSourceTransactionId(row.getSourceTransactionId());
        result.setTransactionType(row.getTransactionType());
        result.setLabelCurrency(row.getLabelCurrency());
        result.setClearingStatus(row.getClearingStatus());
        result.setClearingRevision(row.getClearingRevision());
        result.setClearingRetryCount(row.getClearingRetryCount());
        result.setNextRetryTime(row.getNextRetryTime());
        result.setLastFailureCode(row.getLastFailureCode());
        result.setLastFailureMessage(row.getLastFailureMessage());
        result.setFeePlanId(row.getFeePlanId());
        result.setFeePlanVersionId(row.getFeePlanVersionId());
        result.setFeePlanVersionNo(row.getFeePlanVersionNo());
        result.setGrossLabelAmount(row.getGrossLabelAmount());
        result.setFeeEvaluationStatus(row.getFeeEvaluationStatus());
        result.setSettlementStatus(row.getSettlementStatus());
        result.setSettlementCurrency(row.getSettlementCurrency());
        result.setSettlementEligibleDate(row.getSettlementEligibleDate());
        result.setPlatformFeeAmount(row.getPlatformFeeAmount());
        result.setFeeReversalAmount(row.getFeeReversalAmount());
        result.setReserveAmount(row.getReserveAmount());
        result.setReserveReversalAmount(row.getReserveReversalAmount());
        result.setExpectedReserveReleaseDate(row.getExpectedReserveReleaseDate());
        result.setTransactionDateTime(row.getTransactionDateTime());
        result.setTransactionUtcTime(row.getTransactionUtcTime());
        result.setTransactionTimeZone(row.getTransactionTimeZone());
        result.setVersion(row.getVersion());
        return result;
    }

    /** 管理查询强制单季度半开窗口、成对游标和有上限页大小。 */
    private void validateSearchRequest(ClearingRecordSearchRequest request) {
        if (request == null || request.getBeginTime() == null || request.getEndTime() == null
                || !request.getBeginTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("clearing search requires a valid half-open time range");
        }
        LocalDateTime lastIncluded = request.getEndTime().minusNanos(1);
        if (quarterKey(request.getBeginTime()) != quarterKey(lastIncluded)) {
            throw new IllegalArgumentException("clearing search range must stay within one natural quarter");
        }
        if ((request.getCursorTransactionDateTime() == null) != (request.getCursorId() == null)
                || request.getCursorId() != null && request.getCursorId() < 0) {
            throw new IllegalArgumentException("clearing search cursor time and id must be provided together");
        }
        if (request.getLimit() != null && (request.getLimit() < 1 || request.getLimit() > MAX_LIMIT)) {
            throw new IllegalArgumentException("clearing search limit must be between 1 and 1000");
        }
        normalizedStatus(request);
    }

    private int quarterKey(LocalDateTime value) {
        return value.getYear() * 10 + (value.getMonthValue() - 1) / 3 + 1;
    }

    private String normalizedStatus(ClearingRecordSearchRequest request) {
        String value = trim(request.getClearingStatus());
        if (value == null) {
            return null;
        }
        try {
            return ClearingStateEnum.valueOf(value.toUpperCase(java.util.Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported clearing status", exception);
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ClearingTransactionLine toTransactionLine(ClearingTransactionDetailDO row) {
        ClearingTransactionLine result = new ClearingTransactionLine();
        result.setClearingDetailNo(row.getClearingDetailNo());
        result.setClearingRevision(row.getClearingRevision());
        result.setLineNo(row.getLineNo());
        result.setItemType(row.getItemType());
        result.setFeeCategory(row.getFeeCategory());
        result.setRiskServiceType(row.getRiskServiceType());
        result.setItemCode(row.getItemCode());
        result.setItemName(row.getItemName());
        result.setDirection(row.getDirection());
        result.setLabelCurrency(row.getLabelCurrency());
        result.setLabelAmount(row.getLabelAmount());
        result.setComponentType(row.getComponentType());
        result.setBasisCurrency(row.getBasisCurrency());
        result.setBasisAmount(row.getBasisAmount());
        result.setAmount(row.getAmount());
        result.setCurrency(row.getCurrency());
        result.setCurrencyExponent(row.getCurrencyExponent());
        result.setPercentageRate(row.getPercentageRate());
        result.setFixedAmountUsd(row.getFixedAmountUsd());
        result.setMinimumAmountUsd(row.getMinimumAmountUsd());
        result.setMaximumAmountUsd(row.getMaximumAmountUsd());
        result.setLimitEvaluationStatus(row.getLimitEvaluationStatus());
        result.setAppliedLimit(row.getAppliedLimit());
        result.setFormulaSnapshot(row.getFormulaSnapshot());
        result.setRecordStatus(row.getRecordStatus());
        return result;
    }

    /** 转换保证金原子事实时保留标签币种、方向和剩余金额口径。 */
    private ClearingReserveLine toReserveLine(ClearingReserveDetailDO row) {
        ClearingReserveLine result = new ClearingReserveLine();
        result.setReserveClearingDetailNo(row.getReserveClearingDetailNo());
        result.setOriginalTransactionId(row.getOriginalTransactionId());
        result.setSourceReserveDetailNo(row.getSourceReserveDetailNo());
        result.setClearingRevision(row.getClearingRevision());
        result.setLineNo(row.getLineNo());
        result.setReserveActionType(row.getReserveActionType());
        result.setItemCode(row.getItemCode());
        result.setItemName(row.getItemName());
        result.setDirection(row.getDirection());
        result.setReserveCurrency(row.getReserveCurrency());
        result.setReserveCurrencyExponent(row.getReserveCurrencyExponent());
        result.setBasisAmount(row.getBasisAmount());
        result.setReserveRate(row.getReserveRate());
        result.setRetainedAmount(row.getRetainedAmount());
        result.setReturnedAmount(row.getReturnedAmount());
        result.setReleasedAmount(row.getReleasedAmount());
        result.setAdjustmentAmount(row.getAdjustmentAmount());
        result.setRemainingAmount(row.getRemainingAmount());
        result.setExpectedReserveReleaseDate(row.getExpectedReserveReleaseDate());
        result.setFormulaSnapshot(row.getFormulaSnapshot());
        result.setRecordStatus(row.getRecordStatus());
        return result;
    }
}
