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
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentAuthenticationRecordService
 * @date : 2026-08-17 18:35
 * @email : scott_x@163.com
 * @description : 3DS认证审计实现，通过交易逻辑数据源按交易时间分片持久化安全摘要，禁止保存渠道原文、HTML、CAVV和令牌
 * @status : create
 */
@Service
public class DefaultPaymentAuthenticationRecordService implements PaymentAuthenticationRecordService {

    /**
     * 默认时间时区常量，统一 {@code DefaultPaymentAuthenticationRecordService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";
    /**
     * 认证类型，用于区分 {@code DefaultPaymentAuthenticationRecordService} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String AUTHENTICATION_TYPE = "3DS";
    /**
     * 认证来源常量，统一 {@code DefaultPaymentAuthenticationRecordService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String AUTHENTICATION_SOURCE = "CHANNEL";
    /**
     * {@code RESULT_CODE_MAX_LENGTH}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int RESULT_CODE_MAX_LENGTH = 64;
    /**
     * {@code RESULT_MESSAGE_MAX_LENGTH}常量，统一 {@code DefaultPaymentAuthenticationRecordService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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
     * 记录渠道返回的 3DS 或其它支付认证结果。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
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
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param failureCode 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
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
     * @param attemptDO 已从数据库读取或准备持久化的记录对象，状态、版本和审计字段必须保持一致
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
