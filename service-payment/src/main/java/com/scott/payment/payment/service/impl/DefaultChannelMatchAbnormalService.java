package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.config.ChannelMatchAbnormalProperties;
import com.scott.payment.payment.domain.reconciliation.ChannelMatchAbnormalLevelEnum;
import com.scott.payment.payment.domain.reconciliation.ChannelMatchAbnormalStatusEnum;
import com.scott.payment.payment.domain.reconciliation.ChannelMatchDetectSourceEnum;
import com.scott.payment.payment.domain.reconciliation.ChannelMatchResolutionTypeEnum;
import com.scott.payment.payment.entity.TransactionAbnormalEventDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.TransactionAbnormalEventMapper;
import com.scott.payment.payment.service.ChannelMatchAbnormalService;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionQueryService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalSummary;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalSummaryRow;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AssignCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.CaseReference;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultChannelMatchAbnormalService
 * @date : 2026-08-06 00:00
 * @description : 勾兑异常服务默认实现，使用唯一去重键建案、版本 CAS 处置和真实分片时间重查，不提供人工交易终态修正。
 * @status : create
 */
@Service
@DS(DataSourceName.TRANSACTION)
public class DefaultChannelMatchAbnormalService implements ChannelMatchAbnormalService {

    private static final int MAX_BATCH_REQUERY = 100;
    private static final String STORAGE_TIME_ZONE = TransactionShardingProperties.REQUIRED_ZONE_ID;

    private final TransactionAbnormalEventMapper abnormalEventMapper;
    private final TransactionRecordService transactionRecordService;
    private final TransactionQueryService transactionQueryService;
    private final TransactionChannelMatchService channelMatchService;
    private final GlobalIdGenerator globalIdGenerator;
    private final ChannelMatchAbnormalProperties properties;
    private final LocalDateTime registeredNodeBegin;

    /** 创建勾兑异常服务并解析当前已发布分片起点。 */
    public DefaultChannelMatchAbnormalService(TransactionAbnormalEventMapper abnormalEventMapper,
                                              TransactionRecordService transactionRecordService,
                                              TransactionQueryService transactionQueryService,
                                              TransactionChannelMatchService channelMatchService,
                                              GlobalIdGenerator globalIdGenerator,
                                              ChannelMatchAbnormalProperties properties,
                                              TransactionShardingProperties shardingProperties) {
        this.abnormalEventMapper = abnormalEventMapper;
        this.transactionRecordService = transactionRecordService;
        this.transactionQueryService = transactionQueryService;
        this.channelMatchService = channelMatchService;
        this.globalIdGenerator = globalIdGenerator;
        this.properties = properties;
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
    }

    /** 查询案件分页及完整条件统计。 */
    @Override
    public AbnormalSearchResponse search(AbnormalQuery query) {
        AbnormalQuery safeQuery = normalize(query);
        LocalDateTime endExclusive = safeQuery.getEndTime().plusNanos(1_000_000L);
        long total = abnormalEventMapper.count(safeQuery, safeQuery.getBeginTime(), endExclusive);
        long offset = (safeQuery.safePageNo() - 1) * safeQuery.safePageSize();
        List<AbnormalRecord> records = offset < total
                ? abnormalEventMapper.selectPage(safeQuery, safeQuery.getBeginTime(), endExclusive,
                offset, safeQuery.safePageSize())
                : List.of();
        AbnormalSearchResponse response = new AbnormalSearchResponse();
        response.setPage(PageResult.of(total, safeQuery.safePageNo(), safeQuery.safePageSize(), records));
        response.setSummary(toSummary(abnormalEventMapper.selectSummary(
                safeQuery, safeQuery.getBeginTime(), endExclusive)));
        return response;
    }

    /** 查询精确案件详情并复用交易详情时间线。 */
    @Override
    public AbnormalDetailResponse detail(String eventId, LocalDateTime transactionDateTime) {
        AbnormalRecord record = requireRecord(eventId, transactionDateTime);
        AbnormalDetailResponse response = new AbnormalDetailResponse();
        response.setAbnormality(record);
        if (record.getRootTransactionDateTime() != null) {
            response.setTransactionDetail(transactionQueryService.detail(
                    record.getTransactionId(), record.getTransactionDateTime(), record.getRootTransactionDateTime()));
        }
        return response;
    }

    /** 领取或转派活动案件。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbnormalRecord assign(String eventId, AssignCommand command) {
        if (command == null || command.getTransactionDateTime() == null || command.getExpectedVersion() == null
                || !StringUtils.hasText(command.getOperatorId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        int updated = abnormalEventMapper.assign(eventId, command.getTransactionDateTime(),
                command.getExpectedVersion(), command.getOperatorId(), command.getOperatorName(), LocalDateTime.now());
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT);
        }
        return requireRecord(eventId, command.getTransactionDateTime());
    }

    /** 仅允许确认无需修改或忽略，不能从请求中指定交易目标状态。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbnormalRecord resolve(String eventId, ResolveCommand command) {
        if (command == null || command.getTransactionDateTime() == null || command.getExpectedVersion() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        ChannelMatchResolutionTypeEnum resolution =
                ChannelMatchResolutionTypeEnum.fromCode(command.getResolutionType());
        if (resolution == null || !resolution.manuallyAllowed()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "resolution type is not allowed");
        }
        if (!StringUtils.hasText(command.getReason())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "resolution reason is required");
        }
        String targetStatus = resolution == ChannelMatchResolutionTypeEnum.IGNORED
                ? ChannelMatchAbnormalStatusEnum.IGNORED.getCode()
                : ChannelMatchAbnormalStatusEnum.RESOLVED.getCode();
        int updated = abnormalEventMapper.resolve(eventId, command.getTransactionDateTime(),
                command.getExpectedVersion(), targetStatus, resolution.getCode(),
                command.getReferenceId(), LocalDateTime.now());
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT);
        }
        return requireRecord(eventId, command.getTransactionDateTime());
    }

    /** 使用案件保存的真实分片时间同步执行一次渠道 QUERY。 */
    @Override
    public AbnormalRecord requery(String eventId, RequeryCommand command) {
        if (command == null || command.getTransactionDateTime() == null || command.getExpectedVersion() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        AbnormalRecord current = requireRecord(eventId, command.getTransactionDateTime());
        if (!command.getExpectedVersion().equals(current.getVersion())
                || isTerminal(current.getEventStatus())) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT);
        }
        if (channelMatchService.matchOne(current.getTransactionId(), current.getTransactionDateTime()).getMatchedCount() > 0) {
            autoResolve(current.getTransactionId(), current.getTransactionDateTime(),
                    "MANUAL_REQUERY:" + eventId, LocalDateTime.now());
        }
        return requireRecord(eventId, command.getTransactionDateTime());
    }

    /** 批量重查隔离单笔失败，避免部分案件阻断整个批次。 */
    @Override
    public BatchRequeryResult batchRequery(BatchRequeryCommand command) {
        List<CaseReference> cases = command == null || command.getCases() == null
                ? List.of() : command.getCases();
        if (cases.isEmpty() || cases.size() > MAX_BATCH_REQUERY) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "batch requery supports 1 to 100 cases");
        }
        BatchRequeryResult result = new BatchRequeryResult();
        result.setRequestedCount(cases.size());
        for (CaseReference reference : cases) {
            try {
                RequeryCommand requeryCommand = new RequeryCommand();
                requeryCommand.setTransactionDateTime(reference.getTransactionDateTime());
                requeryCommand.setExpectedVersion(reference.getExpectedVersion());
                requery(reference.getEventId(), requeryCommand);
                result.setAcceptedCount(result.getAcceptedCount() + 1);
            } catch (RuntimeException exception) {
                result.setFailedCount(result.getFailedCount() + 1);
                result.getFailedEventIds().add(reference == null ? null : reference.getEventId());
            }
        }
        return result;
    }

    /** 达到阈值后以数据库唯一键原子新增、计数或重新打开案件。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordReviewRequired(TransactionOperationDO operationDO,
                                     String abnormalType,
                                     String description,
                                     String matchResult,
                                     String sourceRecordId,
                                     LocalDateTime seenTime) {
        recordReviewRequired(operationDO, abnormalType, description, matchResult, sourceRecordId, null, seenTime);
    }

    /** 达到阈值或发现确定性金额异常后，保存渠道查询结构化金额快照。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordReviewRequired(TransactionOperationDO operationDO,
                                     String abnormalType,
                                     String description,
                                     String matchResult,
                                     String sourceRecordId,
                                     ChannelPaymentResponse channelResponse,
                                     LocalDateTime seenTime) {
        if (!properties.isEnabled()) {
            return;
        }
        if (operationDO == null || !StringUtils.hasText(operationDO.getTransactionId())
                || operationDO.getTransactionDateTime() == null || !StringUtils.hasText(abnormalType)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime now = seenTime == null ? LocalDateTime.now() : seenTime;
        TransactionOrderDO order = transactionRecordService.findOrder(
                operationDO.getTransactionDateTime(), operationDO.getOperationId());
        TransactionAbnormalEventDO row = new TransactionAbnormalEventDO();
        row.setAbnormalEventId("ABN" + globalIdGenerator.nextId());
        row.setTransactionId(operationDO.getTransactionId());
        row.setOperationId(operationDO.getOperationId());
        row.setAbnormalType(abnormalType);
        row.setAbnormalLevel(normalizeLevel(properties.getDefaultLevel()));
        row.setEventStatus(ChannelMatchAbnormalStatusEnum.OPEN.getCode());
        row.setSourceRecordType("CHANNEL_QUERY");
        row.setSourceRecordId(sourceRecordId);
        row.setAbnormalDescription(safeLength(description, 512));
        row.setRawReferenceJson(buildEvidence(operationDO, abnormalType, matchResult, sourceRecordId));
        row.setFirstSeenTime(now);
        row.setLastSeenTime(now);
        row.setTransactionDateTime(operationDO.getTransactionDateTime());
        row.setTransactionUtcTime(operationDO.getTransactionUtcTime());
        row.setTransactionTimeZone(operationDO.getTransactionTimeZone());
        row.setDeduplicationKey(abnormalType + ":" + operationDO.getTransactionId());
        row.setMerchantId(operationDO.getMerchantId());
        row.setMerchantOrderNo(operationDO.getMerchantOrderNo());
        row.setSourceTransactionId(operationDO.getSourceTransactionId());
        // 当前动作事实不保存源动作分片时间；无法可靠恢复时留空，禁止用当前动作时间伪造路由值。
        row.setSourceTransactionDateTime(null);
        row.setRootTransactionDateTime(order == null
                ? operationDO.getTransactionDateTime() : order.getTransactionDateTime());
        row.setTransactionType(operationDO.getTransactionType());
        row.setPlatformStatus(operationDO.getTransactionStatus());
        row.setChannelCode(operationDO.getChannelCode());
        row.setChannelOrderNo(operationDO.getChannelOrderNo());
        row.setChannelTransactionId(operationDO.getChannelTransactionId());
        row.setChannelStatus(channelResponse != null && StringUtils.hasText(channelResponse.getRawChannelStatus())
                ? channelResponse.getRawChannelStatus() : operationDO.getChannelStatus());
        row.setChannelMatchResult(matchResult);
        row.setDetectSource(ChannelMatchDetectSourceEnum.AUTO_QUERY.getCode());
        String platformCurrency = normalizeCurrency(operationDO.getTransactionCurrency());
        String channelCurrency = normalizeCurrency(
                channelResponse == null ? null : channelResponse.getChannelCurrency());
        BigDecimal platformAmount = operationDO.getTransactionAmount();
        BigDecimal channelAmount = channelResponse == null ? null : channelResponse.getChannelAmount();
        row.setPlatformCurrency(platformCurrency);
        row.setPlatformAmount(operationDO.getTransactionAmount());
        row.setChannelCurrency(channelCurrency);
        row.setChannelAmount(channelAmount);
        if (platformCurrency != null && platformCurrency.equals(channelCurrency)
                && platformAmount != null && channelAmount != null) {
            row.setAmountDifference(channelAmount.subtract(platformAmount));
        }
        row.setCurrencyExponent(operationDO.getCurrencyExponent());
        row.setOccurrenceCount(1);
        row.setMerchantNotifyRequired(0);
        row.setVersion(0);
        row.setDeleted(0);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        abnormalEventMapper.upsertOccurrence(row);
    }

    private String normalizeCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : null;
    }

    /** 正常状态机完成后只关闭活动案件。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoResolve(String transactionId,
                           LocalDateTime transactionDateTime,
                           String referenceId,
                           LocalDateTime resolvedTime) {
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            return 0;
        }
        return abnormalEventMapper.resolveActiveByTransaction(transactionId, transactionDateTime,
                referenceId, resolvedTime == null ? LocalDateTime.now() : resolvedTime);
    }

    private AbnormalRecord requireRecord(String eventId, LocalDateTime transactionDateTime) {
        if (!StringUtils.hasText(eventId) || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        AbnormalRecord record = abnormalEventMapper.selectRecord(eventId, transactionDateTime);
        if (record == null) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_NOT_FOUND);
        }
        return record;
    }

    private AbnormalQuery normalize(AbnormalQuery query) {
        AbnormalQuery safe = query == null ? new AbnormalQuery() : query;
        if (safe.getMinimumOccurrenceCount() != null && safe.getMinimumOccurrenceCount() <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        ZoneId queryZone = resolveQueryZone(safe.getQueryTimeZone());
        ZoneId storageZone = ZoneId.of(STORAGE_TIME_ZONE);
        boolean useRegisteredNodeBegin = safe.getBeginTime() == null;
        LocalDateTime queryBegin = safe.getBeginTime();
        LocalDateTime queryEnd = safe.getEndTime() == null ? LocalDateTime.now(queryZone) : safe.getEndTime();
        if (!useRegisteredNodeBegin && queryBegin.isAfter(queryEnd)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "abnormal time range is invalid");
        }
        safe.setBeginTime(useRegisteredNodeBegin
                ? registeredNodeBegin : convertBetweenZones(queryBegin, queryZone, storageZone));
        safe.setEndTime(convertBetweenZones(queryEnd, queryZone, storageZone));
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

    private AbnormalSummary toSummary(AbnormalSummaryRow row) {
        AbnormalSummary summary = new AbnormalSummary();
        if (row == null) {
            return summary;
        }
        summary.setTotalCount(value(row.getTotalCount()));
        summary.setOpenCount(value(row.getOpenCount()));
        summary.setProcessingCount(value(row.getProcessingCount()));
        summary.setResolvedCount(value(row.getResolvedCount()));
        summary.setIgnoredCount(value(row.getIgnoredCount()));
        summary.setHighOrCriticalCount(value(row.getHighOrCriticalCount()));
        return summary;
    }

    private String buildEvidence(TransactionOperationDO operationDO,
                                 String abnormalType,
                                 String matchResult,
                                 String sourceRecordId) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("abnormalType", abnormalType);
        evidence.put("matchResult", matchResult);
        evidence.put("sourceRecordId", sourceRecordId);
        evidence.put("channelMatchCount", value(operationDO.getChannelMatchCount()) + 1L);
        return safeLength(JsonUtils.toJsonString(evidence), 4000);
    }

    private String normalizeLevel(String level) {
        for (ChannelMatchAbnormalLevelEnum value : ChannelMatchAbnormalLevelEnum.values()) {
            if (value.getCode().equals(level)) {
                return level;
            }
        }
        return ChannelMatchAbnormalLevelEnum.HIGH.getCode();
    }

    private boolean isTerminal(String status) {
        return ChannelMatchAbnormalStatusEnum.RESOLVED.getCode().equals(status)
                || ChannelMatchAbnormalStatusEnum.IGNORED.getCode().equals(status);
    }

    private long value(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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
