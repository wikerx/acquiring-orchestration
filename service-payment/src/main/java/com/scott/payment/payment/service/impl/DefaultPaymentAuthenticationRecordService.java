package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.TransactionAuthenticationInfoDO;
import com.scott.payment.payment.mapper.TransactionAuthenticationInfoMapper;
import com.scott.payment.payment.service.PaymentAuthenticationRecordService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 3DS 认证阶段的安全审计服务。
 *
 * <p>每个交易与认证阶段使用确定性身份执行 upsert，只保存状态、协议标识、重定向地址摘要和
 * 受控诊断字段。渠道原始报文、HTML、CAVV、PAN、CVV、令牌及查询参数不得进入认证审计表。</p>
 */
@Service
public class DefaultPaymentAuthenticationRecordService implements PaymentAuthenticationRecordService {

    /** 无时区交易时间的业务解释时区。 */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";
    /** 当前审计记录的认证协议类型。 */
    private static final String AUTHENTICATION_TYPE = "3DS";
    /** 表示认证结果由平台渠道适配器获得，而非商户直接提供。 */
    private static final String AUTHENTICATION_SOURCE = "CHANNEL";
    /** 认证结果码字段最大长度，超长内容在持久化前截断。 */
    private static final int RESULT_CODE_MAX_LENGTH = 64;
    /** 脱敏诊断信息最大长度，避免渠道异常正文无限进入审计表。 */
    private static final int RESULT_MESSAGE_MAX_LENGTH = 512;
    private static final Pattern LONG_DIGIT_SEQUENCE = Pattern.compile("(?<![0-9])[0-9]{10,19}(?![0-9])");
    private static final Pattern SENSITIVE_QUERY_PARAMETER = Pattern.compile(
            "(?i)((?:token|secret|password|key|nonce|iv|creq|cres|paRes|md)=)[^&\\s]+"
    );

    private final TransactionAuthenticationInfoMapper mapper;

    public DefaultPaymentAuthenticationRecordService(TransactionAuthenticationInfoMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记录渠道返回的 3DS 阶段结果。
     *
     * @param request  平台统一渠道认证请求
     * @param response 渠道认证响应；为空时按结果未确定记录
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public void recordChannelResult(ChannelThreeDsAuthenticationRequest request,
                                    ChannelThreeDsAuthenticationResponse response) {
        ChannelThreeDsPhase phase = response != null && response.getPhase() != null
                ? response.getPhase() : request.getPhase();
        ChannelThreeDsStatus status = response == null || response.getStatus() == null
                ? ChannelThreeDsStatus.PROCESSING : response.getStatus();
        persist(request, response, phase, status,
                response == null ? "EMPTY_CHANNEL_RESPONSE" : response.getFailureCode());
    }

    /**
     * 记录渠道认证失败事实，保留可审计的失败码和脱敏摘要。
     * @param request     平台统一渠道认证请求
     * @param status      受控认证状态
     * @param failureCode 不含渠道正文的失败类型
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public void recordChannelFailure(ChannelThreeDsAuthenticationRequest request,
                                     ChannelThreeDsStatus status,
                                     String failureCode) {
        persist(request, null, request.getPhase(),
                status == null ? ChannelThreeDsStatus.PROCESSING : status, failureCode);
    }

    /**
     * 记录渠道认证超时事实，供状态机和运营排障使用。
     * @param attemptDO 已持久化的 Checkout 付款尝试
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public void recordTimeout(PaymentCheckoutAttemptDO attemptDO) {
        if (attemptDO == null) {
            return;
        }
        ChannelThreeDsAuthenticationRequest request = new ChannelThreeDsAuthenticationRequest();
        request.setChannelCode(attemptDO.getChannelCode());
        request.setOperationId(attemptDO.getOperationId());
        request.setTransactionId(attemptDO.getTransactionId());
        request.setTransactionDateTime(attemptDO.getTransactionDateTime());
        request.setAuthenticationTransactionId(attemptDO.getThreeDsTransactionId());
        request.setPhase(timeoutPhase(attemptDO.getThreeDsStatus()));
        ChannelThreeDsAuthenticationResponse response = new ChannelThreeDsAuthenticationResponse();
        response.setPhase(request.getPhase());
        response.setStatus(ChannelThreeDsStatus.FAILED);
        response.setAuthenticationTransactionId(attemptDO.getThreeDsTransactionId());
        response.setThreeDsVersion(attemptDO.getThreeDsVersion());
        response.setThreeDsServerTransactionId(attemptDO.getThreeDsServerTransactionId());
        response.setAcsTransactionId(attemptDO.getAcsTransactionId());
        response.setDsTransactionId(attemptDO.getDsTransactionId());
        response.setEci(attemptDO.getEci());
        persist(request, response, request.getPhase(), ChannelThreeDsStatus.FAILED,
                "THREE_DS_AUTHENTICATION_TIMEOUT");
    }

    private void persist(ChannelThreeDsAuthenticationRequest request,
                         ChannelThreeDsAuthenticationResponse response,
                         ChannelThreeDsPhase phase,
                         ChannelThreeDsStatus status,
                         String failureCode) {
        requireIdentity(request, phase);
        LocalDateTime now = LocalDateTime.now();
        TransactionAuthenticationInfoDO row = new TransactionAuthenticationInfoDO();
        row.setAuthenticationInfoId(authenticationInfoId(request, phase));
        row.setTransactionId(request.getTransactionId());
        row.setOperationId(request.getOperationId());
        row.setAuthenticationType(AUTHENTICATION_TYPE);
        row.setAuthenticationStatus(authenticationStatus(status));
        row.setAuthenticationSource(AUTHENTICATION_SOURCE);
        row.setThreeDsVersion(response == null ? null : response.getThreeDsVersion());
        row.setThreeDsTransactionId(firstText(
                response == null ? null : response.getThreeDsTransactionId(),
                response == null ? null : response.getAuthenticationTransactionId(),
                request.getAuthenticationTransactionId()));
        row.setThreeDsServerTransactionId(response == null ? null : response.getThreeDsServerTransactionId());
        row.setAcsTransactionId(response == null ? null : response.getAcsTransactionId());
        row.setDsTransactionId(response == null ? null : response.getDsTransactionId());
        row.setEci(response == null ? null : response.getEci());
        // CAVV 只用于当次渠道授权，认证审计永久留空，避免认证值扩散到普通业务表。
        row.setCavv(null);
        row.setLiabilityShift(hasLiabilityShift(response) ? 1 : 0);
        row.setChallengeRequired(ChannelThreeDsStatus.CHALLENGE_REQUIRED.equals(status) ? 1 : 0);
        row.setChallengeStatus(challengeStatus(phase, status));
        row.setAuthenticationRedirectUrlHash(redirectHash(request, response));
        row.setAuthenticationResultCode(resultCode(status, failureCode));
        row.setAuthenticationResultMessage(resultMessage(status, response));
        row.setAuthenticationTime(isTerminal(status) ? now : null);
        row.setAuthenticationExtraJson(extraJson(request, response, phase, status));
        row.setTransactionDateTime(request.getTransactionDateTime());
        row.setTransactionUtcTime(toUtcTime(request.getTransactionDateTime()));
        row.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        // 确定性 authenticationInfoId 使同一阶段重试更新同一条记录，不重复插入审计事实。
        mapper.upsertPhase(row);
    }

    private void requireIdentity(ChannelThreeDsAuthenticationRequest request, ChannelThreeDsPhase phase) {
        if (request == null || !StringUtils.hasText(request.getTransactionId())
                || request.getTransactionDateTime() == null || phase == null) {
            throw new IllegalArgumentException("3DS authentication audit identity is incomplete");
        }
    }

    private String authenticationInfoId(ChannelThreeDsAuthenticationRequest request,
                                        ChannelThreeDsPhase phase) {
        return sha256Hex(String.join("|",
                request.getTransactionId(),
                StringUtils.hasText(request.getAuthenticationTransactionId())
                        ? request.getAuthenticationTransactionId() : request.getTransactionId(),
                phase.name()));
    }

    private String authenticationStatus(ChannelThreeDsStatus status) {
        if (ChannelThreeDsStatus.PASSED.equals(status)) {
            return "AUTHENTICATED";
        }
        if (ChannelThreeDsStatus.FAILED.equals(status)) {
            return "FAILED";
        }
        return "ATTEMPTED";
    }

    private String challengeStatus(ChannelThreeDsPhase phase, ChannelThreeDsStatus status) {
        if (ChannelThreeDsStatus.CHALLENGE_REQUIRED.equals(status)) {
            return "REQUIRED";
        }
        if (ChannelThreeDsStatus.PASSED.equals(status)
                && (ChannelThreeDsPhase.AUTHENTICATE.equals(phase)
                || ChannelThreeDsPhase.VERIFY.equals(phase))) {
            return "COMPLETED";
        }
        if (ChannelThreeDsStatus.FAILED.equals(status)
                && (ChannelThreeDsPhase.AUTHENTICATE.equals(phase)
                || ChannelThreeDsPhase.VERIFY.equals(phase))) {
            return "FAILED";
        }
        return null;
    }

    private String redirectHash(ChannelThreeDsAuthenticationRequest request,
                                ChannelThreeDsAuthenticationResponse response) {
        String redirect = firstText(response == null ? null : response.getRedirectUrl(),
                request.getRedirectResponseUrl());
        return StringUtils.hasText(redirect) ? sha256Hex(redirect) : null;
    }

    private String resultCode(ChannelThreeDsStatus status, String failureCode) {
        String value = StringUtils.hasText(failureCode) ? failureCode : status.name();
        String safe = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safe.length() <= RESULT_CODE_MAX_LENGTH ? safe : safe.substring(0, RESULT_CODE_MAX_LENGTH);
    }

    private String resultMessage(ChannelThreeDsStatus status,
                                 ChannelThreeDsAuthenticationResponse response) {
        if (ChannelThreeDsStatus.FAILED.equals(status)
                && response != null && StringUtils.hasText(response.getFailureMessage())) {
            return safeDiagnosticMessage(response.getFailureMessage());
        }
        return switch (status) {
            case PASSED -> "3DS authentication completed";
            case FAILED -> "3DS authentication failed";
            case CHALLENGE_REQUIRED -> "3DS cardholder challenge is required";
            case METHOD_REQUIRED -> "3DS browser method is required";
            case READY_TO_AUTHENTICATE -> "3DS initialization completed";
            case PROCESSING -> "3DS authentication outcome is not yet final";
        };
    }

    private String extraJson(ChannelThreeDsAuthenticationRequest request,
                             ChannelThreeDsAuthenticationResponse response,
                             ChannelThreeDsPhase phase,
                             ChannelThreeDsStatus status) {
        // 使用字段白名单构造摘要，禁止直接序列化 request/response 或渠道 extension 全量内容。
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("phase", phase.name());
        summary.put("channelCode", safeCode(request.getChannelCode(), 64));
        summary.put("channelStatus", status.name());
        String providerStatus = response == null ? null : safeCode(response.getThreeDsStatus(), 64);
        if (StringUtils.hasText(providerStatus)) {
            summary.put("providerStatus", providerStatus);
        }
        putIfText(summary, "providerResult", safeCode(extensionValue(response, "providerResult"), 64));
        putIfText(summary, "errorField", safeProtocolField(extensionValue(response, "errorField"), 128));
        putIfText(summary, "validationType", safeCode(extensionValue(response, "validationType"), 64));
        putIfText(summary, "httpStatus", safeCode(extensionValue(response, "httpStatus"), 8));
        return JsonUtils.toJsonString(summary);
    }

    private String extensionValue(ChannelThreeDsAuthenticationResponse response, String key) {
        return response == null || response.getExtension() == null ? null : response.getExtension().get(key);
    }

    private void putIfText(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String safeProtocolField(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String safe = value.trim().replaceAll("[^A-Za-z0-9_.\\[\\]-]", "_");
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private String safeDiagnosticMessage(String value) {
        String safe = value.replaceAll("[\\r\\n\\t]+", " ");
        safe = SENSITIVE_QUERY_PARAMETER.matcher(safe).replaceAll("$1***");
        safe = LONG_DIGIT_SEQUENCE.matcher(safe).replaceAll("***");
        safe = safe.replaceAll("\\s{2,}", " ").trim();
        return safe.length() <= RESULT_MESSAGE_MAX_LENGTH
                ? safe : safe.substring(0, RESULT_MESSAGE_MAX_LENGTH);
    }

    private String safeCode(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String safe = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.:-]", "_");
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private boolean hasLiabilityShift(ChannelThreeDsAuthenticationResponse response) {
        return response != null && (StringUtils.hasText(response.getEci())
                || StringUtils.hasText(response.getCavv()));
    }

    private boolean isTerminal(ChannelThreeDsStatus status) {
        return ChannelThreeDsStatus.PASSED.equals(status) || ChannelThreeDsStatus.FAILED.equals(status);
    }

    private ChannelThreeDsPhase timeoutPhase(String currentStatus) {
        if (ChannelThreeDsStatus.CHALLENGE_REQUIRED.name().equals(currentStatus)) {
            return ChannelThreeDsPhase.VERIFY;
        }
        if (ChannelThreeDsStatus.METHOD_REQUIRED.name().equals(currentStatus)) {
            return ChannelThreeDsPhase.AUTHENTICATE;
        }
        return ChannelThreeDsPhase.INITIALIZE;
    }

    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime) {
        return transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
