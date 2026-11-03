package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
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
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AssignCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.CaseReference;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 渠道勾兑异常案件服务。
 *
 * <p>异常案件按“异常类型 + 平台交易号”唯一去重，人工领取和处置均使用期望版本 CAS。人工操作只能
 * 管理案件或触发真实渠道重查，不能直接指定支付交易终态，交易状态仍由统一渠道结果状态机推进。</p>
 */
@Service
@Slf4j
public class DefaultChannelMatchAbnormalService implements ChannelMatchAbnormalService {

    /**
     * 单批主动重查上限，限制同步渠道查询对线程池和渠道限流配额的冲击。
     */
    private static final int MAX_BATCH_REQUERY = 100;

    private final TransactionAbnormalEventMapper abnormalEventMapper;
    private final TransactionRecordService transactionRecordService;
    private final TransactionChannelMatchService channelMatchService;
    private final GlobalIdGenerator globalIdGenerator;
    private final ChannelMatchAbnormalProperties properties;

    /**
     * 创建勾兑异常命令与建案服务。
     *
     * @param abnormalEventMapper 异常案件写入、精确读取和版本更新 Mapper
     * @param transactionRecordService 交易事实读取服务
     * @param channelMatchService 渠道主动查询和正常状态机勾兑服务
     * @param globalIdGenerator 全局案件号生成器
     * @param properties 自动异常建案配置
     */
    public DefaultChannelMatchAbnormalService(TransactionAbnormalEventMapper abnormalEventMapper,
                                              TransactionRecordService transactionRecordService,
                                              TransactionChannelMatchService channelMatchService,
                                              GlobalIdGenerator globalIdGenerator,
                                              ChannelMatchAbnormalProperties properties) {
        this.abnormalEventMapper = abnormalEventMapper;
        this.transactionRecordService = transactionRecordService;
        this.channelMatchService = channelMatchService;
        this.globalIdGenerator = globalIdGenerator;
        this.properties = properties;
    }

    /**
     * 领取或转派活动案件，使用期望版本防止并发覆盖。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间、期望版本和可信操作人
     * @return 领取或转派后的最新案件记录
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public AbnormalRecord assign(String eventId, AssignCommand command) {
        if (command == null || command.getTransactionDateTime() == null || command.getExpectedVersion() == null
                || !StringUtils.hasText(command.getOperatorId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        // 期望版本使领取和转派保持原子性，避免两个运营人员互相覆盖负责人。
        int updated = abnormalEventMapper.assign(eventId, command.getTransactionDateTime(),
                command.getExpectedVersion(), command.getOperatorId(), command.getOperatorName(), LocalDateTime.now());
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT);
        }
        AbnormalRecord assigned = requireRecord(eventId, command.getTransactionDateTime());
        log.info("渠道勾兑异常案件已领取或转派, event: CHANNEL_MATCH_ABNORMAL_ASSIGNED, eventId: {}, version: {}",
                eventId, assigned.getVersion());
        return assigned;
    }

    /**
     * 确认无需修改或忽略案件，不能从请求中指定交易目标状态。
     *
     * @param eventId 勾兑异常案件号
     * @param command 处置类型、原因、真实分片时间和期望版本
     * @return 处置后的最新案件记录
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
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
        // 人工处置只关闭或忽略案件，不接受任意交易目标状态，资金终态仍由渠道结果状态机负责。
        int updated = abnormalEventMapper.resolve(eventId, command.getTransactionDateTime(),
                command.getExpectedVersion(), targetStatus, resolution.getCode(),
                command.getReferenceId(), LocalDateTime.now());
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT);
        }
        AbnormalRecord resolved = requireRecord(eventId, command.getTransactionDateTime());
        log.info("渠道勾兑异常案件已处置, event: CHANNEL_MATCH_ABNORMAL_RESOLVED, eventId: {}, resolutionType: {}, status: {}, version: {}",
                eventId, resolution.getCode(), resolved.getEventStatus(), resolved.getVersion());
        return resolved;
    }

    /**
     * 使用案件保存的真实分片时间同步执行一次渠道查询。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间和期望案件版本
     * @return 重新勾兑后的最新案件记录
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public AbnormalRecord requery(String eventId, RequeryCommand command) {
        if (command == null || command.getTransactionDateTime() == null || command.getExpectedVersion() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        AbnormalRecord current = requireRecord(eventId, command.getTransactionDateTime());
        if (!command.getExpectedVersion().equals(current.getVersion())
                || isTerminal(current.getEventStatus())) {
            throw new ServiceException(ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT);
        }
        // 重查只触发真实渠道 QUERY；只有统一勾兑链确认一致后才自动关闭案件。
        if (channelMatchService.matchOne(current.getTransactionId(), current.getTransactionDateTime()).getMatchedCount() > 0) {
            autoResolve(current.getTransactionId(), current.getTransactionDateTime(),
                    "MANUAL_REQUERY:" + eventId, LocalDateTime.now());
        }
        AbnormalRecord reloaded = requireRecord(eventId, command.getTransactionDateTime());
        log.info("渠道勾兑异常案件重查完成, event: CHANNEL_MATCH_ABNORMAL_REQUERY_COMPLETED, eventId: {}, status: {}, version: {}",
                eventId, reloaded.getEventStatus(), reloaded.getVersion());
        return reloaded;
    }

    /**
     * 批量重查并隔离单笔失败，避免部分案件阻断整个批次。
     *
     * @param command 待重查案件及其真实分片时间、期望版本
     * @return 批量受理和失败统计
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
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
                log.warn("渠道勾兑异常案件批量重查单笔失败, event: CHANNEL_MATCH_ABNORMAL_BATCH_ITEM_FAILED, eventId: {}, exceptionType: {}",
                        reference == null ? null : reference.getEventId(), exception.getClass().getSimpleName());
            }
        }
        log.info("渠道勾兑异常案件批量重查完成, event: CHANNEL_MATCH_ABNORMAL_BATCH_COMPLETED, requestedCount: {}, acceptedCount: {}, failedCount: {}",
                result.getRequestedCount(), result.getAcceptedCount(), result.getFailedCount());
        return result;
    }

    /**
     * 达到阈值后以数据库唯一键原子新增、计数或重新打开案件。
     *
     * @param operationDO 交易动作快照
     * @param abnormalType 异常类型稳定编码
     * @param description 脱敏异常说明
     * @param matchResult 勾兑结果摘要
     * @param sourceRecordId 渠道查询请求引用，可为空
     * @param seenTime 本次发现时间
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void recordReviewRequired(TransactionOperationDO operationDO,
                                     String abnormalType,
                                     String description,
                                     String matchResult,
                                     String sourceRecordId,
                                     LocalDateTime seenTime) {
        recordReviewRequired(operationDO, abnormalType, description, matchResult, sourceRecordId, null, seenTime);
    }

    /**
     * 达到阈值或发现确定性金额异常后，保存渠道查询结构化金额快照。
     *
     * @param operationDO 交易动作快照
     * @param abnormalType 异常类型稳定编码
     * @param description 脱敏异常说明
     * @param matchResult 勾兑结果摘要
     * @param sourceRecordId 渠道查询请求引用，可为空
     * @param channelResponse 渠道明确返回的币种和主币种单位金额，可为空
     * @param seenTime 本次发现时间
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
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
        // 数据库唯一去重键使重复扫描只增加 occurrenceCount，不会创建多个活动案件。
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
        log.warn("渠道勾兑异常已记录待复核案件, event: CHANNEL_MATCH_ABNORMAL_REVIEW_REQUIRED, transactionId: {}, abnormalType: {}, channelMatchResult: {}",
                operationDO.getTransactionId(), abnormalType, matchResult);
    }

    /**
     * 规范化币种。
     */
    private String normalizeCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 正常状态机确认一致结果后关闭活动案件，不修改交易状态。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 交易真实分片时间
     * @param referenceId 自动恢复依据引用，可为空
     * @param resolvedTime 案件关闭时间，为空时使用当前时间
     * @return 关闭案件数
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int autoResolve(String transactionId,
                           LocalDateTime transactionDateTime,
                           String referenceId,
                           LocalDateTime resolvedTime) {
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            return 0;
        }
        int resolved = abnormalEventMapper.resolveActiveByTransaction(transactionId, transactionDateTime,
                referenceId, resolvedTime == null ? LocalDateTime.now() : resolvedTime);
        if (resolved > 0) {
            log.info("渠道勾兑异常案件已自动关闭, event: CHANNEL_MATCH_ABNORMAL_AUTO_RESOLVED, transactionId: {}, resolvedCount: {}",
                    transactionId, resolved);
        }
        return resolved;
    }

    /**
     * 锁定并返回指定勾兑异常案件，缺少定位信息或记录不存在时明确失败。
     *
     * @param eventId 异常案件唯一标识
     * @param transactionDateTime 原交易分片时间
     * @return 当前异常案件及其版本信息
     */
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

    /**
     * 构建脱敏的渠道勾兑异常证据。
     *
     * @param operationDO 发生异常的交易动作事实
     * @param abnormalType 异常类型
     * @param matchResult 渠道勾兑结果
     * @param sourceRecordId 触发异常的查询或回调记录标识
     * @return 长度不超过数据库字段上限的证据 JSON
     */
    private String buildEvidence(TransactionOperationDO operationDO,
                                 String abnormalType,
                                 String matchResult,
                                 String sourceRecordId) {
        ChannelMatchEvidence evidence = new ChannelMatchEvidence(
                abnormalType,
                matchResult,
                sourceRecordId,
                value(operationDO.getChannelMatchCount()) + 1L);
        return safeLength(JsonUtils.toJsonString(evidence), 4000);
    }

    /**
     * 规范化勾兑异常级别，未知值按高优先级处理。
     *
     * @param level 待校验的异常级别
     * @return 平台支持的异常级别编码
     */
    private String normalizeLevel(String level) {
        for (ChannelMatchAbnormalLevelEnum value : ChannelMatchAbnormalLevelEnum.values()) {
            if (value.getCode().equals(level)) {
                return level;
            }
        }
        return ChannelMatchAbnormalLevelEnum.HIGH.getCode();
    }

    /**
     * 判断异常案件是否已经关闭。
     *
     * @param status 异常案件状态
     * @return 已解决或已忽略时返回 {@code true}
     */
    private boolean isTerminal(String status) {
        return ChannelMatchAbnormalStatusEnum.RESOLVED.getCode().equals(status)
                || ChannelMatchAbnormalStatusEnum.IGNORED.getCode().equals(status);
    }

    /**
     * 将可空数据库计数转换为零起始的长整数。
     *
     * @param value 数据库返回的计数
     * @return 非空计数值；空值返回零
     */
    private long value(Number value) {
        return value == null ? 0L : value.longValue();
    }

    /**
     * 将文本截断到持久化字段允许的长度。
     *
     * @param value 待持久化文本
     * @param maxLength 数据库字段允许的最大字符数
     * @return 未超过上限的文本
     */
    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 渠道勾兑异常的脱敏证据快照。
     *
     * @param abnormalType 异常类型
     * @param matchResult 渠道勾兑结果
     * @param sourceRecordId 触发异常的记录标识
     * @param channelMatchCount 当前交易累计勾兑次数
     */
    private record ChannelMatchEvidence(String abnormalType,
                                        String matchResult,
                                        String sourceRecordId,
                                        long channelMatchCount) {
    }

}
