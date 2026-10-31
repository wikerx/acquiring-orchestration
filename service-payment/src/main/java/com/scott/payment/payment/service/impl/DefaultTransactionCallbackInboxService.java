package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.api.internal.dto.TransactionCallbackInboxAcceptResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.domain.callback.TransactionCallbackInboxStatusEnum;
import com.scott.payment.payment.entity.TransactionCallbackInboxDO;
import com.scott.payment.payment.mapper.TransactionCallbackInboxMapper;
import com.scott.payment.payment.service.TransactionCallbackInboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 渠道回调 Inbox 的持久化与状态服务。
 *
 * <p>回调按渠道、凭据提示和渠道事件号生成幂等键，并由数据库唯一约束完成最终去重。
 * Worker 后续通过状态与版本 CAS 抢占、完成或重试，防止多实例及超时重领后的旧 Worker
 * 重复推进交易。Inbox 只保存标准化载荷、脱敏请求头和报文摘要。</p>
 */
@Service
public class DefaultTransactionCallbackInboxService implements TransactionCallbackInboxService {

    private static final String INBOX_PREFIX = "CBI";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String DUPLICATE = "DUPLICATE";
    private static final int DEFAULT_MAX_RETRY_COUNT = 12;
    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_PAYLOAD_LENGTH = 131_072;
    private static final int MAX_ERROR_LENGTH = 256;
    private static final int MAX_CHANNEL_CODE_LENGTH = 32;
    private static final int MAX_CREDENTIAL_HINT_LENGTH = 128;
    private static final int MAX_CALLBACK_EVENT_ID_LENGTH = 256;
    private static final long MAX_RETRY_DELAY_SECONDS = 3_600L;
    private static final String SHA256_HEX_PATTERN = "[0-9a-fA-F]{64}";

    private final TransactionCallbackInboxMapper inboxMapper;
    private final Clock clock;

    /** 创建生产环境使用的 Inbox 服务。 */
    @Autowired
    public DefaultTransactionCallbackInboxService(TransactionCallbackInboxMapper inboxMapper) {
        this(inboxMapper, Clock.system(ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID)));
    }

    /** 测试可注入固定时钟，确保重试时间断言稳定。 */
    DefaultTransactionCallbackInboxService(TransactionCallbackInboxMapper inboxMapper, Clock clock) {
        this.inboxMapper = inboxMapper;
        this.clock = clock;
    }

    /**
     * 在单个本地事务中原子受理回调并回读唯一记录；事务提交失败时调用方不会收到成功 ACK。
     *
     * @param commandDTO 已完成协议验签、IP 校验和标准化解析的回调命令
     * @return 首次受理或数据库幂等命中结果
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public TransactionCallbackInboxAcceptResultDTO accept(TransactionChannelCallbackCommandDTO commandDTO) {
        validate(commandDTO);
        LocalDateTime now = LocalDateTime.now(clock);
        String channelCode = normalizeChannelCode(commandDTO.getChannelCode());
        String idempotencyKey = buildIdempotencyKey(commandDTO);
        String inboxId = PaymentOrderNoGenerator.nextOrderNo(INBOX_PREFIX, now);

        TransactionCallbackInboxDO inbox = buildInbox(
                commandDTO, channelCode, idempotencyKey, inboxId, now);
        // upsert 与唯一索引共同完成并发受理；应用层预查不能替代数据库最终幂等。
        inboxMapper.upsertInbox(inbox);

        // 始终回读唯一记录，使重复回调返回首次 Inbox 身份而不是新生成的临时编号。
        TransactionCallbackInboxDO persisted = inboxMapper.selectByIdempotency(channelCode, idempotencyKey);
        if (persisted == null) {
            throw new IllegalStateException("callback inbox upsert did not return a persisted record");
        }
        return result(persisted, inboxId.equals(persisted.getInboxId()) ? ACCEPTED : DUPLICATE);
    }

    /** 查询到期待处理回调并将单批数量限制在 1 至 500。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(readOnly = true)
    public List<TransactionCallbackInboxDO> listDue(LocalDateTime now, int limit) {
        LocalDateTime actualNow = now == null ? LocalDateTime.now(clock) : now;
        int actualLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        return inboxMapper.selectDue(
                actualNow,
                actualLimit,
                TransactionCallbackInboxStatusEnum.RECEIVED.name(),
                TransactionCallbackInboxStatusEnum.RETRY.name());
    }

    /** 使用查询版本和待处理状态 CAS 抢占记录；成功后同步更新内存对象版本。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean claim(TransactionCallbackInboxDO inbox, LocalDateTime now) {
        validatePersistedInbox(inbox);
        LocalDateTime actualNow = now == null ? LocalDateTime.now(clock) : now;
        int affectedRows = inboxMapper.claim(
                inbox.getId(),
                inbox.getVersion(),
                actualNow,
                TransactionCallbackInboxStatusEnum.RECEIVED.name(),
                TransactionCallbackInboxStatusEnum.RETRY.name(),
                TransactionCallbackInboxStatusEnum.PROCESSING.name());
        if (affectedRows == 1) {
            inbox.setVersion(inbox.getVersion() + 1);
            inbox.setInboxStatus(TransactionCallbackInboxStatusEnum.PROCESSING.name());
            inbox.setUpdateTime(actualNow);
            return true;
        }
        return false;
    }

    /** 使用抢占后的版本号 CAS 标记完成，防止超时重领后的旧 Worker 覆盖新状态。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean markProcessed(TransactionCallbackInboxDO inbox, LocalDateTime now) {
        validatePersistedInbox(inbox);
        LocalDateTime actualNow = now == null ? LocalDateTime.now(clock) : now;
        return inboxMapper.markProcessed(
                inbox.getId(),
                inbox.getVersion(),
                actualNow,
                TransactionCallbackInboxStatusEnum.PROCESSING.name(),
                TransactionCallbackInboxStatusEnum.PROCESSED.name()) == 1;
    }

    /** 记录失败并按指数退避安排重试；达到单条记录的最大次数后进入 CLOSED。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean markFailed(TransactionCallbackInboxDO inbox, Throwable failure, LocalDateTime now) {
        validatePersistedInbox(inbox);
        LocalDateTime actualNow = now == null ? LocalDateTime.now(clock) : now;
        int retryCount = Math.max(inbox.getRetryCount() == null ? 0 : inbox.getRetryCount(), 0);
        int maxRetryCount = Math.max(
                inbox.getMaxRetryCount() == null ? DEFAULT_MAX_RETRY_COUNT : inbox.getMaxRetryCount(), 1);
        boolean closed = retryCount + 1 >= maxRetryCount;
        // 退避从 5 秒指数增长并封顶一小时，达到最大次数后关闭，避免永久热循环。
        long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS, 5L << Math.min(retryCount, 9));
        String nextStatus = closed
                ? TransactionCallbackInboxStatusEnum.CLOSED.name()
                : TransactionCallbackInboxStatusEnum.RETRY.name();
        return inboxMapper.markFailed(
                inbox.getId(),
                inbox.getVersion(),
                nextStatus,
                closed ? null : actualNow.plusSeconds(delaySeconds),
                safeFailureType(failure),
                actualNow,
                TransactionCallbackInboxStatusEnum.PROCESSING.name()) == 1;
    }

    /** 分批恢复进程退出遗留的超时 PROCESSING 记录，同时计入一次失败尝试。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int recoverStaleProcessing(LocalDateTime staleBefore, LocalDateTime now, int limit) {
        if (staleBefore == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "staleBefore is required");
        }
        LocalDateTime actualNow = now == null ? LocalDateTime.now(clock) : now;
        int actualLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        return inboxMapper.recoverStaleProcessing(
                staleBefore,
                actualNow,
                TransactionCallbackInboxStatusEnum.PROCESSING.name(),
                TransactionCallbackInboxStatusEnum.RETRY.name(),
                TransactionCallbackInboxStatusEnum.CLOSED.name(),
                actualLimit);
    }

    private TransactionCallbackInboxDO buildInbox(TransactionChannelCallbackCommandDTO commandDTO,
                                                   String channelCode,
                                                   String idempotencyKey,
                                                   String inboxId,
                                                   LocalDateTime now) {
        TransactionCallbackInboxDO inbox = new TransactionCallbackInboxDO();
        inbox.setInboxId(inboxId);
        inbox.setTraceId(TraceContext.getOrCreateTraceId());
        inbox.setChannelCode(channelCode);
        inbox.setCredentialHint(normalizeOptional(commandDTO.getChannelCredentialHint()));
        inbox.setCallbackEventId(commandDTO.getCallbackEventId().trim());
        inbox.setIdempotencyKey(idempotencyKey);
        inbox.setPayloadJson(safePayload(commandDTO));
        inbox.setRequestHeaderJsonMasked(commandDTO.getRequestHeaders() == null
                ? null : SensitiveDataMaskUtils.maskJsonSafely(
                        JsonUtils.toJsonString(commandDTO.getRequestHeaders())));
        inbox.setRequestBodyLength(commandDTO.getRequestBodyLength());
        inbox.setRequestBodySha256(commandDTO.getRequestBodySha256().toLowerCase(Locale.ROOT));
        inbox.setInboxStatus(TransactionCallbackInboxStatusEnum.RECEIVED.name());
        inbox.setRetryCount(0);
        inbox.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        inbox.setNextRetryTime(now);
        inbox.setReceivedTime(commandDTO.getReceivedTime() == null ? now : commandDTO.getReceivedTime());
        inbox.setVersion(0);
        inbox.setCreateTime(now);
        inbox.setUpdateTime(now);
        return inbox;
    }

    private String safePayload(TransactionChannelCallbackCommandDTO source) {
        // 请求头单独脱敏保存，标准化业务载荷中移除请求头，避免敏感凭据被重复持久化。
        TransactionChannelCallbackCommandDTO copy = JsonUtils.parseObject(
                JsonUtils.toJsonString(source), TransactionChannelCallbackCommandDTO.class);
        copy.setRequestHeaders(null);
        String payload = JsonUtils.toJsonString(copy);
        if (payload == null || payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "callback normalized payload exceeds the allowed size");
        }
        return payload;
    }

    private String buildIdempotencyKey(TransactionChannelCallbackCommandDTO commandDTO) {
        // 凭据提示参与隔离同渠道多账户，但仅保存组合值摘要，不暴露原始凭据线索。
        return sha256(String.join("\n",
                normalizeChannelCode(commandDTO.getChannelCode()),
                StringUtils.hasText(commandDTO.getChannelCredentialHint())
                        ? commandDTO.getChannelCredentialHint().trim() : "-",
                commandDTO.getCallbackEventId().trim()));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private void validate(TransactionChannelCallbackCommandDTO commandDTO) {
        // 只有网关层已完成验签与 IP 校验的回调才允许入箱，Worker 不重新信任原始请求头。
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getChannelCode())
                || !StringUtils.hasText(commandDTO.getCallbackEventId())
                || commandDTO.getChannelCode().trim().length() > MAX_CHANNEL_CODE_LENGTH
                || commandDTO.getCallbackEventId().trim().length() > MAX_CALLBACK_EVENT_ID_LENGTH
                || (StringUtils.hasText(commandDTO.getChannelCredentialHint())
                    && commandDTO.getChannelCredentialHint().trim().length() > MAX_CREDENTIAL_HINT_LENGTH)
                || !Boolean.TRUE.equals(commandDTO.getSignatureValid())
                || !Boolean.TRUE.equals(commandDTO.getIpAllowed())
                || commandDTO.getRequestBodyLength() == null
                || commandDTO.getRequestBodyLength() < 0
                || !StringUtils.hasText(commandDTO.getRequestBodySha256())
                || !commandDTO.getRequestBodySha256().matches(SHA256_HEX_PATTERN)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void validatePersistedInbox(TransactionCallbackInboxDO inbox) {
        if (inbox == null || inbox.getId() == null || inbox.getVersion() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private String normalizeChannelCode(String channelCode) {
        return channelCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeFailureType(Throwable failure) {
        String value = failure == null ? "CallbackProcessingException" : failure.getClass().getSimpleName();
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    private TransactionCallbackInboxAcceptResultDTO result(TransactionCallbackInboxDO inbox,
                                                            String acceptResult) {
        TransactionCallbackInboxAcceptResultDTO result = new TransactionCallbackInboxAcceptResultDTO();
        result.setInboxId(inbox.getInboxId());
        result.setInboxStatus(inbox.getInboxStatus());
        result.setAcceptResult(acceptResult);
        return result;
    }
}
