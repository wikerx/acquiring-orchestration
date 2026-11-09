package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.clearing.ClearingInternalClient;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.InternalReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.InternalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.Response;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.ReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.SubmitRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Admin 标签本金修复编排；校验浏览器参数并从可信登录上下文注入 Maker/Checker。
 */
@Service
public class AdminLabelPrincipalRepairApplicationService {

    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT");
    private static final Pattern REPAIR_NO_PATTERN = Pattern.compile("^LPR[0-9a-f]{32}$");

    private final ClearingInternalClient clearingClient;
    private final AdminMerchantDataScopeResolver scopeResolver;

    public AdminLabelPrincipalRepairApplicationService(ClearingInternalClient clearingClient,
                                                       AdminMerchantDataScopeResolver scopeResolver) {
        this.clearingClient = clearingClient;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 校验浏览器提交的交易和版本前置条件，并注入可信 Maker 后转交清分服务。
     */
    public Response submit(SubmitRequest request) {
        if (request == null || !validText(request.getRequestKey(), 128)
                || !validText(request.getTransactionId(), 64) || request.getTransactionDateTime() == null
                || request.getExpectedFinanceStateVersion() == null
                || request.getExpectedFinanceStateVersion() < 0
                || request.getExpectedClearingRevision() == null
                || request.getExpectedClearingRevision() < 1
                || request.getExpectedCandidateVersion() == null
                || request.getExpectedCandidateVersion() < 0
                || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalSubmitRequest command = new InternalSubmitRequest();
        command.setRequestKey(request.getRequestKey().trim());
        command.setTransactionId(request.getTransactionId().trim());
        command.setTransactionDateTime(request.getTransactionDateTime());
        command.setExpectedFinanceStateVersion(request.getExpectedFinanceStateVersion());
        command.setExpectedClearingRevision(request.getExpectedClearingRevision());
        command.setExpectedCandidateVersion(request.getExpectedCandidateVersion());
        command.setReason(request.getReason().trim());
        command.setSubmitOperator(currentOperator());
        return clearingClient.submitLabelPrincipalRepair(command);
    }

    /**
     * 校验复核决定和申请版本，并注入可信 Checker 后转交清分服务执行状态机。
     */
    public Response review(String repairNo, ReviewRequest request) {
        String decision = request == null || request.getDecision() == null
                ? null : request.getDecision().trim().toUpperCase(Locale.ROOT);
        if (!validRepairNo(repairNo) || request == null
                || request.getExpectedRequestVersion() == null
                || request.getExpectedRequestVersion() < 0 || !DECISIONS.contains(decision)
                || !validText(request.getReviewComment(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalReviewRequest command = new InternalReviewRequest();
        command.setExpectedRequestVersion(request.getExpectedRequestVersion());
        command.setDecision(decision);
        command.setReviewComment(request.getReviewComment().trim());
        command.setReviewOperator(currentOperator());
        return clearingClient.reviewLabelPrincipalRepair(repairNo.trim(), command);
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }

    private boolean validRepairNo(String value) {
        return value != null && REPAIR_NO_PATTERN.matcher(value.trim()).matches();
    }

    /**
     * 历史资金修复只允许拥有全商户数据范围的操作人，防止按钮误配扩大访问范围。
     */
    private String currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        AdminMerchantDataScope scope = scopeResolver.resolve(account);
        if (scope == null || !scope.allMerchants()) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return "admin-account:" + account.getAccountId();
    }
}
