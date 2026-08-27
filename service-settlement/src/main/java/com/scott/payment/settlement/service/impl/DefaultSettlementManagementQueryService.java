package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchDetailResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSearchRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSearchResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSummary;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.NetPosting;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.OperationalState;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.RateLine;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ResultSummaryLine;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchRateDO;
import com.scott.payment.settlement.entity.SettlementOperationalStateDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementResultSummaryDO;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.mapper.SettlementManagementMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import com.scott.payment.settlement.service.SettlementManagementQueryService;
import com.scott.payment.settlement.support.SettlementBatchNumberFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementManagementQueryService
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 默认结算运营查询实现；严格限制扫描窗口，并保持不可变金额、币种和汇率字段原样输出。
 * @status : create
 */
@Service
public class DefaultSettlementManagementQueryService implements SettlementManagementQueryService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;
    private static final long MAX_DATE_SPAN_DAYS = 92;

    private final SettlementManagementMapper managementMapper;
    private final SettlementBatchRateMapper rateMapper;
    private final SettlementResultMapper resultMapper;
    private final SettlementBatchNumberFormatter numberFormatter;

    public DefaultSettlementManagementQueryService(SettlementManagementMapper managementMapper,
                                                   SettlementBatchRateMapper rateMapper,
                                                   SettlementResultMapper resultMapper,
                                                   SettlementBatchNumberFormatter numberFormatter) {
        this.managementMapper = managementMapper;
        this.rateMapper = rateMapper;
        this.resultMapper = resultMapper;
        this.numberFormatter = numberFormatter;
    }

    /** 执行日期窗口和主键游标查询，limit+1 只用于判断是否还有下一页。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(readOnly = true)
    public BatchSearchResponse search(BatchSearchRequest request) {
        validateSearch(request);
        int limit = request.getLimit() == null ? DEFAULT_LIMIT : request.getLimit();
        List<SettlementBatchDO> rows = managementMapper.selectBatches(
                trim(request.getSettlementBatchNo()), trim(request.getMerchantId()),
                enumName(request.getBatchType(), SettlementBatchType.class),
                enumName(request.getBatchStatus(), SettlementBatchStatus.class),
                request.getBeginBusinessDate(), request.getEndBusinessDate(),
                request.getCursorId(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<SettlementBatchDO> page = hasMore ? rows.subList(0, limit) : rows;
        BatchSearchResponse response = new BatchSearchResponse();
        response.setRecords(page.stream().map(this::summary).toList());
        response.setHasMore(hasMore);
        if (!page.isEmpty()) {
            response.setNextCursorId(page.get(page.size() - 1).getId());
        }
        return response;
    }

    /** 读取单批的有界运营详情；不存在的最终入账结果保持为空。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(readOnly = true)
    public BatchDetailResponse detail(String settlementBatchNo) {
        String batchNo = requiredBatchNo(settlementBatchNo);
        SettlementBatchDO batch = managementMapper.selectBatch(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("settlement batch does not exist");
        }
        BatchDetailResponse response = new BatchDetailResponse();
        response.setBatch(summary(batch));
        response.setRates(rateMapper.selectByBatchNo(batchNo).stream().map(this::rate).toList());
        response.setResultSummaries(resultMapper.selectSummariesByBatch(batchNo)
                .stream().map(this::resultSummary).toList());
        response.setNetPosting(netPosting(resultMapper.selectNetPosting(batchNo)));
        response.setOperationalState(operationalState(managementMapper.selectOperationalState(batchNo)));
        return response;
    }

    private void validateSearch(BatchSearchRequest request) {
        if (request == null || request.getBeginBusinessDate() == null
                || request.getEndBusinessDate() == null
                || request.getBeginBusinessDate().isAfter(request.getEndBusinessDate())
                || ChronoUnit.DAYS.between(request.getBeginBusinessDate(),
                request.getEndBusinessDate()) > MAX_DATE_SPAN_DAYS) {
            throw new IllegalArgumentException("settlement search business date range must be within 93 days");
        }
        if (request.getCursorId() != null && request.getCursorId() < 1) {
            throw new IllegalArgumentException("settlement search cursor id must be positive");
        }
        if (request.getLimit() != null && (request.getLimit() < 1 || request.getLimit() > MAX_LIMIT)) {
            throw new IllegalArgumentException("settlement search limit must be between 1 and 200");
        }
        if (trim(request.getSettlementBatchNo()) != null) {
            requiredBatchNo(request.getSettlementBatchNo());
        }
        if (trim(request.getMerchantId()) != null && trim(request.getMerchantId()).length() > 64) {
            throw new IllegalArgumentException("settlement search merchant id is too long");
        }
        enumName(request.getBatchType(), SettlementBatchType.class);
        enumName(request.getBatchStatus(), SettlementBatchStatus.class);
    }

    private BatchSummary summary(SettlementBatchDO row) {
        BatchSummary value = new BatchSummary();
        value.setId(row.getId());
        value.setSettlementBatchNo(row.getSettlementBatchNo());
        value.setDisplayBatchNo(numberFormatter.displayNumber(row.getBusinessDate(), row.getDailySequence()));
        value.setBusinessDate(row.getBusinessDate());
        value.setBusinessTimeZone(row.getBusinessTimeZone());
        value.setDailySequence(row.getDailySequence());
        value.setMerchantId(row.getMerchantId());
        value.setSettlementProfileId(row.getSettlementProfileId());
        value.setSettlementAccountId(row.getSettlementAccountId());
        value.setTargetCurrency(row.getTargetCurrency());
        value.setTargetCurrencyExponent(row.getTargetCurrencyExponent());
        value.setBatchType(row.getBatchType());
        value.setOriginalBatchNo(row.getOriginalBatchNo());
        value.setBatchStatus(row.getBatchStatus());
        value.setCandidateCount(row.getCandidateCount());
        value.setRetryCount(row.getRetryCount());
        value.setLastFailureStage(row.getLastFailureStage());
        value.setLastFailureCode(row.getLastFailureCode());
        value.setLastFailureMessage(row.getLastFailureMessage());
        value.setRateLockedTime(row.getRateLockedTime());
        value.setCalculatedTime(row.getCalculatedTime());
        value.setPostedTime(row.getPostedTime());
        value.setCancelledTime(row.getCancelledTime());
        value.setVersion(row.getVersion());
        value.setCreateTime(row.getCreateTime());
        value.setUpdateTime(row.getUpdateTime());
        return value;
    }

    private RateLine rate(SettlementBatchRateDO row) {
        RateLine value = new RateLine();
        value.setId(row.getId());
        value.setSourceCurrency(row.getSourceCurrency());
        value.setTargetCurrency(row.getTargetCurrency());
        value.setRateType(row.getRateType());
        value.setDirectRate(row.getDirectRate());
        value.setSourceCurrencyExponent(row.getSourceCurrencyExponent());
        value.setTargetCurrencyExponent(row.getTargetCurrencyExponent());
        value.setRateSource(row.getRateSource());
        value.setQuoteId(row.getQuoteId());
        value.setSourceQuoteDirection(row.getSourceQuoteDirection());
        value.setEffectiveTime(row.getEffectiveTime());
        value.setLockedTime(row.getLockedTime());
        value.setLockedBy(row.getLockedBy());
        return value;
    }

    private ResultSummaryLine resultSummary(SettlementResultSummaryDO row) {
        ResultSummaryLine value = new ResultSummaryLine();
        value.setPaymentType(row.getPaymentType());
        value.setPaymentMethod(row.getPaymentMethod());
        value.setTransactionType(row.getTransactionType());
        value.setResultItemType(row.getResultItemType());
        value.setFeeCategory(row.getFeeCategory());
        value.setDirection(row.getDirection());
        value.setSourceCurrency(row.getSourceCurrency());
        value.setTargetCurrency(row.getTargetCurrency());
        value.setTransactionCount(row.getTransactionCount());
        value.setSourceAmount(row.getSourceAmount());
        value.setTargetAmount(row.getTargetAmount());
        return value;
    }

    private NetPosting netPosting(SettlementResultItemDO row) {
        if (row == null) {
            return null;
        }
        NetPosting value = new NetPosting();
        value.setId(row.getId());
        value.setSettlementResultItemNo(row.getSettlementResultItemNo());
        value.setReversalOfResultItemId(row.getReversalOfResultItemId());
        value.setDirection(row.getDirection());
        value.setTargetAmount(row.getTargetAmount());
        value.setTargetCurrency(row.getTargetCurrency());
        value.setTargetCurrencyExponent(row.getTargetCurrencyExponent());
        value.setLedgerIdempotencyKey(row.getLedgerIdempotencyKey());
        value.setFormulaSnapshot(row.getFormulaSnapshot());
        value.setCreateTime(row.getCreateTime());
        return value;
    }

    private OperationalState operationalState(SettlementOperationalStateDO row) {
        OperationalState value = new OperationalState();
        if (row == null) {
            value.setProjectionTaskCount(0L);
            value.setProjectionCompletedCount(0L);
            value.setProjectionFailedCount(0L);
            value.setOutboxEventCount(0L);
            value.setOutboxSentCount(0L);
            value.setOutboxFailedCount(0L);
            return value;
        }
        value.setProjectionTaskCount(row.getProjectionTaskCount());
        value.setProjectionCompletedCount(row.getProjectionCompletedCount());
        value.setProjectionFailedCount(row.getProjectionFailedCount());
        value.setOutboxEventCount(row.getOutboxEventCount());
        value.setOutboxSentCount(row.getOutboxSentCount());
        value.setOutboxFailedCount(row.getOutboxFailedCount());
        return value;
    }

    private String requiredBatchNo(String value) {
        String batchNo = trim(value);
        if (batchNo == null || !batchNo.matches("SB\\d{8}-\\d{8}")) {
            throw new IllegalArgumentException("settlement batch number format is invalid");
        }
        return batchNo;
    }

    private <E extends Enum<E>> String enumName(String value, Class<E> type) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, normalized.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported settlement query enum", exception);
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
