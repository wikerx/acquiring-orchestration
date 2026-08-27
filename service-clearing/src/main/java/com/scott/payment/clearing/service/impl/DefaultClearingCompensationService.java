package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationRecord;
import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanResponse;
import com.scott.payment.clearing.entity.ClearingCompensationCandidateDO;
import com.scott.payment.clearing.mapper.ClearingCompensationMapper;
import com.scott.payment.clearing.service.ClearingCompensationService;
import com.scott.payment.clearing.service.ClearingRecoveryService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 默认补偿扫描实现；扫描只读，逐条恢复由独立事务服务完成。 */
@Service
public class DefaultClearingCompensationService implements ClearingCompensationService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    private static final long PENDING_TIMEOUT_MINUTES = 5L;
    private static final Set<String> MODES = Set.of("DRY_RUN", "SHADOW_WRITE");

    private final ClearingCompensationMapper compensationMapper;
    private final ClearingRecoveryService recoveryService;
    private final ClearingOperationalMetrics metrics;

    public DefaultClearingCompensationService(ClearingCompensationMapper compensationMapper,
                                              ClearingRecoveryService recoveryService,
                                              ClearingOperationalMetrics metrics) {
        this.compensationMapper = compensationMapper;
        this.recoveryService = recoveryService;
        this.metrics = metrics;
    }

    @Override
    @DS(DataSourceName.TRANSACTION)
    public CompensationScanResponse scan(CompensationScanRequest request, LocalDateTime now) {
        String mode = validate(request, now);
        try {
            return scanValidated(request, now, mode);
        } catch (RuntimeException exception) {
            metrics.recordCompensationFailure(mode);
            throw exception;
        }
    }

    private CompensationScanResponse scanValidated(CompensationScanRequest request,
                                                    LocalDateTime now,
                                                    String mode) {
        int limit = request.getLimit() == null ? DEFAULT_LIMIT : request.getLimit();
        List<ClearingCompensationCandidateDO> rows = compensationMapper.selectCandidates(
                request.getBeginTime(), request.getEndTime(), request.getCursorTransactionDateTime(),
                request.getCursorId(), now.minusMinutes(PENDING_TIMEOUT_MINUTES), now, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ClearingCompensationCandidateDO> page = hasMore ? rows.subList(0, limit) : rows;
        List<CompensationRecord> records = new ArrayList<>(page.size());
        int writeCount = 0;
        int skippedCount = 0;
        for (ClearingCompensationCandidateDO row : page) {
            String result = "DRY_RUN";
            if ("SHADOW_WRITE".equals(mode)) {
                result = recoveryService.recover(row, now);
                if ("SKIPPED_STALE".equals(result) || "ALREADY_SCHEDULED".equals(result)) {
                    skippedCount++;
                } else {
                    writeCount++;
                }
            }
            records.add(toRecord(row, result));
        }
        CompensationScanResponse response = new CompensationScanResponse();
        response.setMode(mode);
        response.setScannedCount(page.size());
        response.setWriteCount(writeCount);
        response.setSkippedCount(skippedCount);
        response.setHasMore(hasMore);
        response.setRecords(List.copyOf(records));
        if (!page.isEmpty()) {
            ClearingCompensationCandidateDO last = page.get(page.size() - 1);
            response.setNextCursorTransactionDateTime(last.getTransactionDateTime());
            response.setNextCursorId(last.getOperationRowId());
        }
        metrics.recordCompensation(mode, page.size(), writeCount, skippedCount);
        return response;
    }

    private String validate(CompensationScanRequest request, LocalDateTime now) {
        if (request == null || now == null || request.getBeginTime() == null || request.getEndTime() == null
                || !request.getBeginTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("compensation scan requires a valid half-open time range");
        }
        if (quarterKey(request.getBeginTime()) != quarterKey(request.getEndTime().minusNanos(1))) {
            throw new IllegalArgumentException("compensation scan range must stay within one natural quarter");
        }
        if ((request.getCursorTransactionDateTime() == null) != (request.getCursorId() == null)) {
            throw new IllegalArgumentException("compensation cursor time and id must be provided together");
        }
        if (request.getLimit() != null && (request.getLimit() < 1 || request.getLimit() > MAX_LIMIT)) {
            throw new IllegalArgumentException("compensation scan limit must be between 1 and 1000");
        }
        String mode = StringUtils.hasText(request.getMode())
                ? request.getMode().trim().toUpperCase(Locale.ROOT) : "DRY_RUN";
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("unsupported compensation scan mode");
        }
        return mode;
    }

    private int quarterKey(LocalDateTime value) {
        return value.getYear() * 10 + (value.getMonthValue() - 1) / 3 + 1;
    }

    private CompensationRecord toRecord(ClearingCompensationCandidateDO row, String result) {
        CompensationRecord record = new CompensationRecord();
        record.setTransactionId(row.getTransactionId());
        record.setOperationId(row.getOperationId());
        record.setMerchantId(row.getMerchantId());
        record.setClearingStatus(row.getClearingStatus());
        record.setReason(row.getFinanceStateId() == null ? "MISSING_FINANCE_STATE" : row.getReason());
        record.setResult(result);
        record.setTransactionDateTime(row.getTransactionDateTime());
        return record;
    }
}
