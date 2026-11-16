package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.DecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.InternalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.InternalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.SubmitRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * 管理端历史标签本金差额调整编排；限定全商户数据范围并注入可信 Maker/Checker 审计。
 */
@Service
public class AdminSettlementPrincipalCorrectionApplicationService {

    private final SettlementInternalClient client;
    private final AdminSettlementQueryService batchQueryService;
    private final AdminMerchantDataScopeResolver dataScopeResolver;

    public AdminSettlementPrincipalCorrectionApplicationService(
            SettlementInternalClient client,
            AdminSettlementQueryService batchQueryService,
            AdminMerchantDataScopeResolver dataScopeResolver) {
        this.client = client;
        this.batchQueryService = batchQueryService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 校验原批次数据范围并提交只冻结、不入账的 Maker 申请。
     */
    public CommandResponse submit(SubmitRequest request, HttpServletRequest servletRequest) {
        if (request == null || !validText(request.getRequestKey(), 128)
                || request.getExpectedBatchVersion() == null || request.getExpectedBatchVersion() < 0
                || !validText(request.getReason(), 400)
                || !validBatchNo(request.getOriginalBatchNo())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAllMerchantAdmin();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        batchQueryService.requireBatchAccess(request.getOriginalBatchNo().trim(), scope);
        InternalSubmitRequest command = new InternalSubmitRequest();
        command.setRequestKey(request.getRequestKey().trim());
        command.setOriginalBatchNo(request.getOriginalBatchNo().trim());
        command.setExpectedBatchVersion(request.getExpectedBatchVersion());
        command.setReason(request.getReason().trim());
        enrich(command, account, servletRequest);
        return client.submitPrincipalCorrection(command);
    }

    /**
     * 仅允许全商户数据范围的不同账号复核，最终异人约束由 settlement 服务状态机执行。
     */
    public CommandResponse decide(String correctionNo,
                                  String decision,
                                  DecisionRequest request,
                                  HttpServletRequest servletRequest) {
        if (!validCorrectionNo(correctionNo)
                || !("APPROVE".equals(decision) || "REJECT".equals(decision))
                || request == null || !validText(request.getRequestKey(), 128)
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || !validText(request.getComment(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAllMerchantAdmin();
        InternalDecisionRequest command = new InternalDecisionRequest();
        command.setRequestKey(request.getRequestKey().trim());
        command.setExpectedVersion(request.getExpectedVersion());
        command.setComment(request.getComment().trim());
        command.setDecision(decision);
        enrich(command, account, servletRequest);
        return client.decidePrincipalCorrection(correctionNo.trim(), command);
    }

    private InternalAuthAccount currentAllMerchantAdmin() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAppId() == null || account.getAccountId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        if (scope == null || !scope.allMerchants()) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return account;
    }

    private void enrich(InternalSubmitRequest command,
                        InternalAuthAccount account,
                        HttpServletRequest request) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(request));
        command.setUserAgent(userAgent(request));
        command.setOperationTime(LocalDateTime.now());
    }

    private void enrich(InternalDecisionRequest command,
                        InternalAuthAccount account,
                        HttpServletRequest request) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(request));
        command.setUserAgent(userAgent(request));
        command.setOperationTime(LocalDateTime.now());
    }

    private String operatorName(InternalAuthAccount account) {
        String value = StringUtils.hasText(account.getRealName()) ? account.getRealName().trim()
                : StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : null;
        if (!validText(value, 128)) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return value;
    }

    private String roleSnapshot(InternalAuthAccount account) {
        String value = account.getRoles() == null ? "" : account.getRoles().stream()
                .filter(StringUtils::hasText).map(String::trim).distinct()
                .sorted(Comparator.naturalOrder()).collect(java.util.stream.Collectors.joining(","));
        return value.isBlank() ? "UNASSIGNED" : value;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        String value = com.scott.payment.admin.application.auth.AdminClientIpResolver.resolve(request);
        if (!validText(value, 64)) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return value;
    }

    private String userAgent(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("User-Agent");
        if (!StringUtils.hasText(value)) {
            return "UNKNOWN";
        }
        value = value.trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }

    private boolean validBatchNo(String value) {
        return StringUtils.hasText(value) && value.trim().matches("SB\\d{8}-\\d{8}");
    }

    private boolean validCorrectionNo(String value) {
        return StringUtils.hasText(value) && value.trim().matches("SPC[0-9a-f]{32}");
    }
}
