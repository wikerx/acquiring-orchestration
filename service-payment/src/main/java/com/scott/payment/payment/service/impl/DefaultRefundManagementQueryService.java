package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.RefundManagementMapper;
import com.scott.payment.payment.mapper.TransactionOrderMapper;
import com.scott.payment.payment.service.RefundManagementQueryService;
import com.scott.payment.payment.service.TransactionQueryService;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundDetailResponse;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundQuery;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundRecord;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundSearchResponse;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundStatusSummaryRow;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundSummary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRefundManagementQueryService
 * @date : 2026-08-06 15:50
 * @email : scott_x@163.com
 * @description : 退款管理查询默认实现，使用交易逻辑表和普通审批表分页，并批量补充生命周期主单真实分片时间。
 * @status : create
 */
@Service
@DS(DataSourceName.TRANSACTION)
public class DefaultRefundManagementQueryService implements RefundManagementQueryService {

    private static final String STORAGE_TIME_ZONE = TransactionShardingProperties.REQUIRED_ZONE_ID;

    private final RefundManagementMapper refundMapper;
    private final TransactionOrderMapper orderMapper;
    private final TransactionQueryService transactionQueryService;
    private final LocalDateTime registeredNodeBegin;

    /**
     * 创建退款管理查询实现。
     *
     * @param refundMapper 退款查询 Mapper
     * @param orderMapper 生命周期主单 Mapper
     * @param transactionQueryService 交易聚合详情服务
     * @param shardingProperties 已发布分片拓扑
     */
    public DefaultRefundManagementQueryService(RefundManagementMapper refundMapper,
                                               TransactionOrderMapper orderMapper,
                                               TransactionQueryService transactionQueryService,
                                               TransactionShardingProperties shardingProperties) {
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.transactionQueryService = transactionQueryService;
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
    }

    /**
     * 查询退款分页和完整条件统计；普通列表默认今日，待审批队列默认覆盖全部已发布分片。
     */
    @Override
    public RefundSearchResponse search(RefundQuery query) {
        RefundQuery safeQuery = normalize(query);
        LocalDateTime endTimeExclusive = exclusiveEnd(safeQuery.getEndTime());
        long total = refundMapper.count(safeQuery, safeQuery.getBeginTime(), endTimeExclusive);
        long offset = (safeQuery.safePageNo() - 1) * safeQuery.safePageSize();
        List<RefundRecord> records = offset < total
                ? refundMapper.selectPage(
                        safeQuery, safeQuery.getBeginTime(), endTimeExclusive,
                        offset, safeQuery.safePageSize())
                : List.of();
        enrichRootTimes(records);

        RefundStatusSummaryRow statusRow = refundMapper.selectStatusSummary(
                safeQuery, safeQuery.getBeginTime(), endTimeExclusive);
        RefundSummary summary = toSummary(statusRow);
        summary.setCurrencyAmounts(refundMapper.selectCurrencySummary(
                safeQuery, safeQuery.getBeginTime(), endTimeExclusive));

        RefundSearchResponse response = new RefundSearchResponse();
        response.setPage(PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), records));
        response.setSummary(summary);
        return response;
    }

    /** 查询精确退款详情，并在能够定位根主单时复用现有交易聚合时间线。 */
    @Override
    public RefundDetailResponse detail(String transactionId,
                                       LocalDateTime transactionDateTime,
                                       String merchantId) {
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        RefundRecord refund = refundMapper.selectOne(transactionId, transactionDateTime, merchantId);
        if (refund == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        enrichRootTimes(List.of(refund));
        RefundDetailResponse response = new RefundDetailResponse();
        response.setRefund(refund);
        if (refund.getRootTransactionDateTime() != null) {
            response.setTransactionDetail(transactionQueryService.detail(
                    transactionId, transactionDateTime, refund.getRootTransactionDateTime()));
        }
        return response;
    }

    private RefundQuery normalize(RefundQuery query) {
        RefundQuery safe = query == null ? new RefundQuery() : query;
        if (safe.getMinimumTransactionAmount() != null
                && safe.getMaximumTransactionAmount() != null
                && safe.getMinimumTransactionAmount().compareTo(safe.getMaximumTransactionAmount()) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "refund amount range is invalid");
        }
        ZoneId queryZone = resolveQueryZone(safe.getQueryTimeZone());
        ZoneId storageZone = ZoneId.of(STORAGE_TIME_ZONE);
        LocalDateTime queryNow = LocalDateTime.now(queryZone);
        boolean useRegisteredNodeBegin = safe.getBeginTime() == null
                && "PENDING".equals(safe.getApprovalStatus());
        LocalDateTime queryBegin = safe.getBeginTime() == null
                ? queryNow.toLocalDate().atStartOfDay() : safe.getBeginTime();
        LocalDateTime queryEnd = safe.getEndTime() == null ? queryNow : safe.getEndTime();
        if (queryBegin.isAfter(queryEnd)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "refund time range is invalid");
        }
        if (safe.getCompleteBeginTime() != null && safe.getCompleteEndTime() != null
                && safe.getCompleteBeginTime().isAfter(safe.getCompleteEndTime())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "refund complete time range is invalid");
        }
        safe.setBeginTime(useRegisteredNodeBegin
                ? registeredNodeBegin : convertBetweenZones(queryBegin, queryZone, storageZone));
        safe.setEndTime(convertBetweenZones(queryEnd, queryZone, storageZone));
        safe.setCompleteBeginTime(convertBetweenZones(safe.getCompleteBeginTime(), queryZone, storageZone));
        safe.setCompleteEndTime(convertBetweenZones(safe.getCompleteEndTime(), queryZone, storageZone));
        safe.setQueryTimeZone(storageZone.getId());
        return safe;
    }

    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : STORAGE_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "queryTimeZone is invalid", exception);
        }
    }

    private String normalizeZoneId(String zone) {
        String normalized = zone.trim();
        String upper = normalized.toUpperCase();
        if ("UTC".equals(upper) || "GMT".equals(upper)) {
            return upper;
        }
        if (upper.startsWith("UTC+") || upper.startsWith("UTC-")
                || upper.startsWith("GMT+") || upper.startsWith("GMT-")) {
            String prefix = upper.substring(0, 3);
            String offset = upper.substring(3);
            if (offset.matches("[+-]\\d{1,2}")) {
                return prefix + String.format("%+03d:00", Integer.parseInt(offset));
            }
            if (offset.matches("[+-]\\d{1,2}:\\d{2}")) {
                String[] parts = offset.substring(1).split(":");
                return prefix + offset.charAt(0)
                        + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
            }
        }
        return normalized;
    }

    private LocalDateTime convertBetweenZones(LocalDateTime value, ZoneId sourceZone, ZoneId targetZone) {
        return value == null ? null
                : value.atZone(sourceZone).withZoneSameInstant(targetZone).toLocalDateTime();
    }

    private void enrichRootTimes(List<RefundRecord> records) {
        List<String> operationIds = records.stream()
                .map(RefundRecord::getOperationId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (operationIds.isEmpty()) {
            return;
        }
        Map<String, LocalDateTime> rootTimes = new LinkedHashMap<>();
        for (TransactionOrderDO order : orderMapper.selectByOperationIds(
                operationIds, registeredNodeBegin, LocalDateTime.now().plusDays(1))) {
            rootTimes.put(order.getOperationId(), order.getTransactionDateTime());
        }
        records.forEach(record -> record.setRootTransactionDateTime(rootTimes.get(record.getOperationId())));
    }

    private RefundSummary toSummary(RefundStatusSummaryRow row) {
        RefundSummary summary = new RefundSummary();
        if (row == null) {
            return summary;
        }
        summary.setTotalCount(value(row.getTotalCount()));
        summary.setPendingApprovalCount(value(row.getPendingApprovalCount()));
        summary.setProcessingCount(value(row.getProcessingCount()));
        summary.setSuccessCount(value(row.getSuccessCount()));
        summary.setFailedOrRejectedCount(value(row.getFailedOrRejectedCount()));
        return summary;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private LocalDateTime exclusiveEnd(LocalDateTime value) {
        return value.plusNanos(1_000_000L);
    }

    private LocalDateTime resolveRegisteredNodeBegin(List<String> physicalNodes) {
        if (physicalNodes == null || physicalNodes.isEmpty()) {
            return LocalDate.now().withDayOfMonth(1).atStartOfDay();
        }
        return physicalNodes.stream()
                .filter(value -> value != null && value.matches("\\d{4}0[1-4]"))
                .map(this::quarterBegin)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDate.now().withDayOfMonth(1).atStartOfDay());
    }

    private LocalDateTime quarterBegin(String suffix) {
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(5, 6));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }
}
