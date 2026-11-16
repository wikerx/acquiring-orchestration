package com.scott.payment.settlement.application;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileCommandResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileCreateRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileReadinessResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileVersionCreateRequest;
import com.scott.payment.settlement.entity.MerchantSettlementAccountDO;
import com.scott.payment.settlement.entity.MerchantSettlementProfileCommandAuditDO;
import com.scott.payment.settlement.entity.MerchantSettlementProfileDO;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 结算档案命令应用服务，负责账户/币种校验、活动版本切换、持久化幂等和开通 readiness。
 */
@Service
public class SettlementProfileApplicationService {

    private static final Set<String> PROCESSING_MODES = Set.of("AUTO_POST", "AUTO_REVIEW", "MANUAL");
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_CREATE_VERSION = "CREATE_VERSION";

    private final MerchantSettlementProfileMapper profileMapper;

    /**
     * 创建结算档案应用服务。
     *
     * @param profileMapper 结算档案、资金账户和命令审计数据访问
     */
    public SettlementProfileApplicationService(MerchantSettlementProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    /**
     * 创建商户首个活动结算档案；请求键先落审计表以串行化并发重放。
     *
     * @param request service-admin 注入可信操作审计的创建命令
     * @return 新建或幂等重放的活动档案
     */
    @Transactional(rollbackFor = Exception.class)
    public ProfileCommandResponse create(ProfileCreateRequest request) {
        ProfileCreateRequest command = normalize(request);
        String resultProfileNo = profileNo(ACTION_CREATE, command.getMerchantId(), null, command.getRequestKey());
        String fingerprint = fingerprint(
                ACTION_CREATE, command.getMerchantId(), command.getSettlementAccountId(), command.getTargetCurrency(),
                command.getBusinessTimeZone(), command.getDailyCutoffTime(), command.getProcessingMode());
        MerchantSettlementProfileCommandAuditDO audit = audit(
                ACTION_CREATE, command.getMerchantId(), null, resultProfileNo, null, fingerprint,
                command.getRequestKey(), command.getReason(), command.getOperatorId(), command.getOperatorName(),
                command.getRoleSnapshot(), command.getClientIp(), command.getUserAgent(), command.getOperationTime());
        if (profileMapper.insertCommandAuditIfAbsent(audit) == 0) {
            return replay(audit);
        }

        MerchantSettlementAccountDO account = requireAccount(
                command.getSettlementAccountId(), command.getMerchantId(), command.getTargetCurrency(), true);
        if (profileMapper.selectActiveByMerchantForUpdate(command.getMerchantId()) != null) {
            throw new ServiceException("SETTLEMENT_PROFILE_ALREADY_ACTIVE",
                    "merchant already has an active settlement profile");
        }
        int exponent = requireCurrencyExponent(command.getTargetCurrency());
        LocalDateTime now = LocalDateTime.now();
        MerchantSettlementProfileDO profile = newProfile(
                resultProfileNo, command.getMerchantId(), account.getId(), command.getTargetCurrency(), exponent,
                command.getBusinessTimeZone(), command.getDailyCutoffTime(), command.getProcessingMode(),
                0L, now);
        if (profileMapper.insertProfile(profile) != 1) {
            throw new IllegalStateException("settlement profile insert failed");
        }
        return response(profile, false);
    }

    /**
     * 通过新建活动版本替代旧档案；旧行退役后仍可被历史候选和批次引用。
     *
     * @param sourceProfileNo 当前活动档案号
     * @param request 新调度参数、期望版本和可信操作审计
     * @return 新建或幂等重放的活动档案版本
     */
    @Transactional(rollbackFor = Exception.class)
    public ProfileCommandResponse createVersion(String sourceProfileNo,
                                                ProfileVersionCreateRequest request) {
        String normalizedSource = requireText(sourceProfileNo, 64, "source settlement profile is required");
        ProfileVersionCreateRequest command = normalize(request);
        MerchantSettlementProfileDO source = profileMapper.selectByProfileNoForUpdate(normalizedSource);
        if (source == null) {
            throw new ServiceException("SETTLEMENT_PROFILE_NOT_FOUND", "settlement profile does not exist");
        }
        String resultProfileNo = profileNo(
                ACTION_CREATE_VERSION, source.getMerchantId(), normalizedSource, command.getRequestKey());
        String fingerprint = fingerprint(
                ACTION_CREATE_VERSION, normalizedSource, command.getExpectedVersion(),
                command.getBusinessTimeZone(), command.getDailyCutoffTime(), command.getProcessingMode());
        MerchantSettlementProfileCommandAuditDO audit = audit(
                ACTION_CREATE_VERSION, source.getMerchantId(), normalizedSource, resultProfileNo,
                command.getExpectedVersion(), fingerprint, command.getRequestKey(), command.getReason(),
                command.getOperatorId(), command.getOperatorName(), command.getRoleSnapshot(),
                command.getClientIp(), command.getUserAgent(), command.getOperationTime());
        if (profileMapper.insertCommandAuditIfAbsent(audit) == 0) {
            return replay(audit);
        }
        if (!"ACTIVE".equals(source.getProfileStatus()) || !Integer.valueOf(1).equals(source.getActiveSlot())
                || !Objects.equals(command.getExpectedVersion(), source.getVersion())) {
            throw new ServiceException("SETTLEMENT_PROFILE_VERSION_CONFLICT",
                    "settlement profile changed; refresh and retry");
        }

        requireAccount(source.getSettlementAccountId(), source.getMerchantId(), source.getTargetCurrency(), true);
        int exponent = requireCurrencyExponent(source.getTargetCurrency());
        if (!Objects.equals(exponent, source.getTargetCurrencyExponent())) {
            throw new ServiceException("SETTLEMENT_PROFILE_CURRENCY_EXPONENT_MISMATCH",
                    "settlement profile currency exponent differs from ISO dictionary");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate effectiveDate = LocalDate.now(zone(command.getBusinessTimeZone()));
        if (profileMapper.retireActiveProfile(
                normalizedSource, command.getExpectedVersion(), effectiveDate, now) != 1) {
            throw new ServiceException("SETTLEMENT_PROFILE_VERSION_CONFLICT",
                    "settlement profile changed; refresh and retry");
        }
        MerchantSettlementProfileDO next = newProfile(
                resultProfileNo, source.getMerchantId(), source.getSettlementAccountId(), source.getTargetCurrency(),
                exponent, command.getBusinessTimeZone(), command.getDailyCutoffTime(), command.getProcessingMode(),
                command.getExpectedVersion() + 1, now);
        next.setEffectiveDate(effectiveDate);
        if (profileMapper.insertProfile(next) != 1) {
            throw new IllegalStateException("settlement profile version insert failed");
        }
        return response(next, false);
    }

    /**
     * 查询最终开通所需的结算档案和资金账户完整性，不读取余额或银行敏感资料。
     *
     * @param merchantId 平台商户号
     * @return 稳定问题编码组成的 readiness
     */
    @Transactional(readOnly = true)
    public ProfileReadinessResponse readiness(String merchantId) {
        String normalizedMerchantId = requireText(merchantId, 64, "merchant is required");
        ProfileReadinessResponse response = new ProfileReadinessResponse();
        response.setMerchantId(normalizedMerchantId);
        List<String> issues = new ArrayList<>();
        MerchantSettlementProfileDO profile = profileMapper.selectActiveByMerchant(normalizedMerchantId);
        if (profile == null) {
            issues.add("SETTLEMENT_PROFILE_NOT_ACTIVE");
            response.setReady(false);
            response.setIssues(issues);
            return response;
        }
        response.setSettlementProfileNo(profile.getSettlementProfileNo());
        MerchantSettlementAccountDO account = profileMapper.selectAccount(profile.getSettlementAccountId());
        if (account == null || !normalizedMerchantId.equals(account.getMerchantId())
                || !"NORMAL".equals(account.getAccountStatus())) {
            issues.add("SETTLEMENT_ACCOUNT_NOT_NORMAL");
        } else if (!profile.getTargetCurrency().equalsIgnoreCase(account.getSettlementCurrency())) {
            issues.add("SETTLEMENT_ACCOUNT_CURRENCY_MISMATCH");
        }
        Integer exponent = profileMapper.selectCurrencyExponent(profile.getTargetCurrency());
        if (exponent == null || !Objects.equals(exponent, profile.getTargetCurrencyExponent())) {
            issues.add("SETTLEMENT_CURRENCY_NOT_SUPPORTED");
        }
        if (!validZone(profile.getBusinessTimeZone())) {
            issues.add("SETTLEMENT_BUSINESS_TIME_ZONE_INVALID");
        }
        if (profile.getDailyCutoffTime() == null) {
            issues.add("SETTLEMENT_DAILY_CUTOFF_NOT_CONFIGURED");
        }
        if (!PROCESSING_MODES.contains(profile.getProcessingMode())) {
            issues.add("SETTLEMENT_PROCESSING_MODE_INVALID");
        }
        response.setReady(issues.isEmpty());
        response.setIssues(List.copyOf(issues));
        return response;
    }

    /**
     * 规范化并校验初始创建命令。
     */
    private ProfileCreateRequest normalize(ProfileCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("settlement profile create request is required");
        }
        request.setRequestKey(requireText(request.getRequestKey(), 64, "request key is required"));
        request.setMerchantId(requireText(request.getMerchantId(), 64, "merchant is required"));
        if (request.getSettlementAccountId() == null || request.getSettlementAccountId() <= 0) {
            throw new IllegalArgumentException("settlement account is required");
        }
        request.setTargetCurrency(requireCurrency(request.getTargetCurrency()));
        normalizeSchedule(request);
        normalizeAudit(request.getReason(), request.getOperatorId(), request.getOperatorName(),
                request.getRoleSnapshot(), request.getClientIp(), request.getUserAgent(), request.getOperationTime());
        request.setReason(request.getReason().trim());
        request.setOperatorName(request.getOperatorName().trim());
        request.setRoleSnapshot(request.getRoleSnapshot().trim());
        request.setClientIp(request.getClientIp().trim());
        request.setUserAgent(request.getUserAgent().trim());
        return request;
    }

    /**
     * 规范化并校验版本创建命令。
     */
    private ProfileVersionCreateRequest normalize(ProfileVersionCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("settlement profile version request is required");
        }
        request.setRequestKey(requireText(request.getRequestKey(), 64, "request key is required"));
        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw new IllegalArgumentException("expected version is invalid");
        }
        normalizeSchedule(request);
        normalizeAudit(request.getReason(), request.getOperatorId(), request.getOperatorName(),
                request.getRoleSnapshot(), request.getClientIp(), request.getUserAgent(), request.getOperationTime());
        request.setReason(request.getReason().trim());
        request.setOperatorName(request.getOperatorName().trim());
        request.setRoleSnapshot(request.getRoleSnapshot().trim());
        request.setClientIp(request.getClientIp().trim());
        request.setUserAgent(request.getUserAgent().trim());
        return request;
    }

    /**
     * 校验初始创建调度字段。
     */
    private void normalizeSchedule(ProfileCreateRequest request) {
        request.setBusinessTimeZone(requireZone(request.getBusinessTimeZone()));
        request.setProcessingMode(requireMode(request.getProcessingMode()));
        if (request.getDailyCutoffTime() == null) {
            throw new IllegalArgumentException("daily cutoff time is required");
        }
    }

    /**
     * 校验新版本调度字段。
     */
    private void normalizeSchedule(ProfileVersionCreateRequest request) {
        request.setBusinessTimeZone(requireZone(request.getBusinessTimeZone()));
        request.setProcessingMode(requireMode(request.getProcessingMode()));
        if (request.getDailyCutoffTime() == null) {
            throw new IllegalArgumentException("daily cutoff time is required");
        }
    }

    /**
     * 校验可信操作审计字段，禁止不完整命令进入持久化状态机。
     */
    private void normalizeAudit(String reason,
                                Long operatorId,
                                String operatorName,
                                String roleSnapshot,
                                String clientIp,
                                String userAgent,
                                LocalDateTime operationTime) {
        requireText(reason, 400, "reason is required");
        if (operatorId == null || operatorId <= 0) {
            throw new IllegalArgumentException("operator is required");
        }
        requireText(operatorName, 128, "operator name is required");
        requireText(roleSnapshot, 1000, "role snapshot is required");
        requireText(clientIp, 64, "client IP is required");
        requireText(userAgent, 500, "user agent is required");
        if (operationTime == null) {
            throw new IllegalArgumentException("operation time is required");
        }
    }

    /**
     * 校验目标资金账户与商户和币种一致；写命令使用行锁防止并发关闭或改币种。
     */
    private MerchantSettlementAccountDO requireAccount(Long accountId,
                                                       String merchantId,
                                                       String currency,
                                                       boolean forUpdate) {
        MerchantSettlementAccountDO account = forUpdate
                ? profileMapper.selectAccountForUpdate(accountId) : profileMapper.selectAccount(accountId);
        if (account == null || !merchantId.equals(account.getMerchantId())) {
            throw new ServiceException("SETTLEMENT_ACCOUNT_NOT_FOUND", "settlement account does not exist");
        }
        if (!"NORMAL".equals(account.getAccountStatus())) {
            throw new ServiceException("SETTLEMENT_ACCOUNT_NOT_NORMAL", "settlement account is not normal");
        }
        if (!currency.equalsIgnoreCase(account.getSettlementCurrency())) {
            throw new ServiceException("SETTLEMENT_ACCOUNT_CURRENCY_MISMATCH",
                    "settlement account currency does not match target currency");
        }
        return account;
    }

    /**
     * 从 ISO 字典读取并校验目标币种 exponent。
     */
    private int requireCurrencyExponent(String currency) {
        Integer exponent = profileMapper.selectCurrencyExponent(currency);
        if (exponent == null || exponent < 0 || exponent > 8) {
            throw new ServiceException("SETTLEMENT_CURRENCY_NOT_SUPPORTED",
                    "settlement currency is not enabled in ISO dictionary");
        }
        return exponent;
    }

    /**
     * 构造只影响后续候选的活动档案版本。
     */
    private MerchantSettlementProfileDO newProfile(String profileNo,
                                                   String merchantId,
                                                   Long accountId,
                                                   String currency,
                                                   int exponent,
                                                   String timeZone,
                                                   java.time.LocalTime cutoff,
                                                   String processingMode,
                                                   long version,
                                                   LocalDateTime now) {
        MerchantSettlementProfileDO row = new MerchantSettlementProfileDO();
        row.setSettlementProfileNo(profileNo);
        row.setMerchantId(merchantId);
        row.setSettlementAccountId(accountId);
        row.setTargetCurrency(currency);
        row.setTargetCurrencyExponent(exponent);
        row.setBusinessTimeZone(timeZone);
        row.setDailyCutoffTime(cutoff);
        row.setProcessingMode(processingMode);
        row.setProfileStatus("ACTIVE");
        row.setActiveSlot(1);
        row.setEffectiveDate(LocalDate.now(zone(timeZone)));
        row.setExpireDate(null);
        row.setVersion(version);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /**
     * 构造不可变命令审计；不保存银行资料、余额或鉴权密钥。
     */
    private MerchantSettlementProfileCommandAuditDO audit(String action,
                                                          String merchantId,
                                                          String sourceProfileNo,
                                                          String resultProfileNo,
                                                          Long expectedVersion,
                                                          String requestFingerprint,
                                                          String requestKey,
                                                          String reason,
                                                          Long operatorId,
                                                          String operatorName,
                                                          String roleSnapshot,
                                                          String clientIp,
                                                          String userAgent,
                                                          LocalDateTime operationTime) {
        MerchantSettlementProfileCommandAuditDO row = new MerchantSettlementProfileCommandAuditDO();
        row.setRequestKey(requestKey);
        row.setRequestFingerprint(requestFingerprint);
        row.setCommandAction(action);
        row.setMerchantId(merchantId);
        row.setSourceProfileNo(sourceProfileNo);
        row.setResultProfileNo(resultProfileNo);
        row.setExpectedVersion(expectedVersion);
        row.setOperatorAccountId(operatorId);
        row.setOperatorAccountName(operatorName);
        row.setOperatorRoleSnapshot(roleSnapshot);
        row.setClientIp(clientIp);
        row.setUserAgent(userAgent);
        row.setReason(reason);
        row.setOperationTime(operationTime);
        row.setCreateTime(LocalDateTime.now());
        return row;
    }

    /**
     * 校验同键命令指纹并返回已提交的档案结果。
     */
    private ProfileCommandResponse replay(MerchantSettlementProfileCommandAuditDO expected) {
        MerchantSettlementProfileCommandAuditDO stored =
                profileMapper.selectCommandAuditForUpdate(expected.getRequestKey());
        if (stored == null || !Objects.equals(expected.getRequestFingerprint(), stored.getRequestFingerprint())
                || !Objects.equals(expected.getCommandAction(), stored.getCommandAction())
                || !Objects.equals(expected.getMerchantId(), stored.getMerchantId())
                || !Objects.equals(expected.getSourceProfileNo(), stored.getSourceProfileNo())) {
            throw new ServiceException("SETTLEMENT_PROFILE_IDEMPOTENCY_CONFLICT",
                    "request key was already used by another settlement profile command");
        }
        MerchantSettlementProfileDO profile = profileMapper.selectByProfileNo(stored.getResultProfileNo());
        if (profile == null) {
            throw new IllegalStateException("settlement profile idempotency result is missing");
        }
        return response(profile, true);
    }

    /**
     * 将持久化档案转换为内部响应，避免把 DO 直接暴露给调用方。
     */
    private ProfileCommandResponse response(MerchantSettlementProfileDO profile, boolean reused) {
        ProfileCommandResponse response = new ProfileCommandResponse();
        response.setId(profile.getId());
        response.setSettlementProfileNo(profile.getSettlementProfileNo());
        response.setMerchantId(profile.getMerchantId());
        response.setSettlementAccountId(profile.getSettlementAccountId());
        response.setTargetCurrency(profile.getTargetCurrency());
        response.setTargetCurrencyExponent(profile.getTargetCurrencyExponent());
        response.setBusinessTimeZone(profile.getBusinessTimeZone());
        response.setDailyCutoffTime(profile.getDailyCutoffTime());
        response.setProcessingMode(profile.getProcessingMode());
        response.setProfileStatus(profile.getProfileStatus());
        response.setVersion(profile.getVersion());
        response.setReused(reused);
        return response;
    }

    /**
     * 基于命令身份生成稳定且不泄露商户资料的档案号。
     */
    private String profileNo(String action, String merchantId, String sourceProfileNo, String requestKey) {
        return "MSP-" + fingerprint(action, merchantId, sourceProfileNo, requestKey).substring(0, 32).toUpperCase(Locale.ROOT);
    }

    /**
     * 生成规范化字段指纹，字段分隔符避免简单拼接产生歧义。
     */
    private String fingerprint(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            if (!canonical.isEmpty()) {
                canonical.append('\u001f');
            }
            canonical.append(value == null ? "<null>" : value);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 校验并规范化 ISO 4217 三位币种。
     */
    private String requireCurrency(String currency) {
        String normalized = requireText(currency, 3, "target currency is required").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("target currency is invalid");
        }
        return normalized;
    }

    /**
     * 校验并规范化结算处理模式。
     */
    private String requireMode(String mode) {
        String normalized = requireText(mode, 16, "processing mode is required").toUpperCase(Locale.ROOT);
        if (!PROCESSING_MODES.contains(normalized)) {
            throw new IllegalArgumentException("processing mode is invalid");
        }
        return normalized;
    }

    /**
     * 校验并规范化 IANA 业务时区。
     */
    private String requireZone(String timeZone) {
        String normalized = requireText(timeZone, 64, "business time zone is required");
        zone(normalized);
        return normalized;
    }

    /**
     * 解析 IANA 时区，不允许使用不可识别的显示文本。
     */
    private ZoneId zone(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("business time zone is invalid", exception);
        }
    }

    /**
     * 判断持久化时区是否仍可由当前 JDK 解析。
     */
    private boolean validZone(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            ZoneId.of(value);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    /**
     * 校验必填文本并去除首尾空白。
     */
    private String requireText(String value, int maxLength, String message) {
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
