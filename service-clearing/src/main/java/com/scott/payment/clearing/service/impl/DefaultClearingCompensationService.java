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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingCompensationService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 默认补偿扫描实现；扫描只读，逐条恢复由独立事务服务完成。
 * @status : update
 */
@Service
public class DefaultClearingCompensationService implements ClearingCompensationService {

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
    /**
     * 等待超时分钟数常量，统一 {@code DefaultClearingCompensationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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

    /** {@inheritDoc} */
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

    /** 使用主键游标逐条调用独立短事务恢复，单条失败不得回滚整页。 */
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

    /** 限制为单季度半开窗口和有上限批量，防止补偿广播所有交易分片。 */
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
