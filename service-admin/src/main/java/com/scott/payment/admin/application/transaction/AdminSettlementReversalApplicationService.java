package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.service.AdminSettlementReversalQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReversalApplicationService
 * @date : 2026-09-01 23:00
 * @email : scott_x@163.com
 * @description : Admin 冲正单本地查询、数据范围和 Maker-Checker 命令编排；注入可信身份、角色、客户端信息和操作时间后调用 settlement 服务。
 * @status : update
 */
@Service
public class AdminSettlementReversalApplicationService {

    private final SettlementInternalClient client;
    private final AdminSettlementQueryService batchQueryService;
    private final AdminSettlementReversalQueryService reversalQueryService;
    private final AdminMerchantDataScopeResolver dataScopeResolver;

    public AdminSettlementReversalApplicationService(SettlementInternalClient client,
                                                     AdminSettlementQueryService batchQueryService,
                                                     AdminSettlementReversalQueryService reversalQueryService,
                                                     AdminMerchantDataScopeResolver dataScopeResolver) {
        this.client = client;
        this.batchQueryService = batchQueryService;
        this.reversalQueryService = reversalQueryService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 查询当前 Admin 数据范围内的冲正单。
     *
     * @param request 冲正单过滤和分页条件
     * @return 冲正单分页
     */
    public PageResult<ReversalSummary> search(ReversalSearchRequest request) {
        InternalAuthAccount account = currentAdminAccount();
        return reversalQueryService.search(request, dataScopeResolver.resolve(account));
    }

    /**
     * 查询当前 Admin 数据范围内的冲正单详情。
     *
     * @param reversalOrderNo 冲正单号
     * @return 冲正申请、复核和执行结果
     */
    public ReversalDetailResponse detail(String reversalOrderNo) {
        requireReversalNo(reversalOrderNo);
        InternalAuthAccount account = currentAdminAccount();
        return reversalQueryService.detail(reversalOrderNo.trim(), dataScopeResolver.resolve(account));
    }

    /**
     * 校验原批次访问权和期望版本后提交冲正申请，申请阶段不直接变更资金。
     *
     * @param request 原批次、请求键、期望版本和原因
     * @param servletRequest 可信客户端审计信息来源
     * @return 待复核冲正申请
     */
    public ReversalCommandResponse submit(ReversalSubmitRequest request, HttpServletRequest servletRequest) {
        if (request == null || !validText(request.getRequestKey(), 128)
                || request.getExpectedBatchVersion() == null || request.getExpectedBatchVersion() < 0
                || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        requireBatchNo(request.getOriginalBatchNo());
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        batchQueryService.requireBatchAccess(request.getOriginalBatchNo().trim(), scope);
        InternalReversalSubmitRequest internal = new InternalReversalSubmitRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setOriginalBatchNo(request.getOriginalBatchNo().trim());
        internal.setExpectedBatchVersion(request.getExpectedBatchVersion());
        internal.setReason(request.getReason().trim());
        enrich(internal, account, servletRequest);
        return client.submitReversal(internal);
    }

    /**
     * 在数据范围校验后提交审批或拒绝命令，Maker-Checker 最终由 settlement 状态机强制执行。
     *
     * @param reversalOrderNo 冲正单号
     * @param decision APPROVE 或 REJECT
     * @param request 请求键、期望版本和复核意见
     * @param servletRequest 可信客户端审计信息来源
     * @return 冲正决策结果
     */
    public ReversalCommandResponse decide(String reversalOrderNo,
                                          String decision,
                                          ReversalDecisionRequest request,
                                          HttpServletRequest servletRequest) {
        requireReversalNo(reversalOrderNo);
        if (!("APPROVE".equals(decision) || "REJECT".equals(decision))
                || request == null || !validText(request.getRequestKey(), 128)
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || !validText(request.getComment(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        reversalQueryService.requireAccess(reversalOrderNo.trim(), scope);
        InternalReversalDecisionRequest internal = new InternalReversalDecisionRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setExpectedVersion(request.getExpectedVersion());
        internal.setComment(request.getComment().trim());
        internal.setDecision(decision);
        enrich(internal, account, servletRequest);
        return client.decideReversal(reversalOrderNo.trim(), internal);
    }

    /** 将可信 Admin 身份、角色快照、客户端地址和操作时间注入冲正申请。 */
    private void enrich(InternalReversalSubmitRequest command,
                        InternalAuthAccount account,
                        HttpServletRequest request) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(request));
        command.setUserAgent(userAgent(request));
        command.setOperationTime(LocalDateTime.now());
    }

    /** 将可信 Admin 身份、角色快照、客户端地址和操作时间注入冲正决策。 */
    private void enrich(InternalReversalDecisionRequest command,
                        InternalAuthAccount account,
                        HttpServletRequest request) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(request));
        command.setUserAgent(userAgent(request));
        command.setOperationTime(LocalDateTime.now());
    }

    /** 只接受具有 appId、accountId 且 appCode=ADMIN 的可信认证账户。 */
    private InternalAuthAccount currentAdminAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAppId() == null || account.getAccountId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    private String operatorName(InternalAuthAccount account) {
        String value = StringUtils.hasText(account.getRealName()) ? account.getRealName().trim()
                : StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : null;
        if (!validText(value, 128)) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        return value;
    }

    /** 将已认证角色去重排序后固化为审计快照，避免集合顺序导致审计事实漂移。 */
    private String roleSnapshot(InternalAuthAccount account) {
        String value = account.getRoles() == null ? "" : account.getRoles().stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.joining(","));
        return value.isBlank() ? "UNASSIGNED" : value;
    }

    /** 解析可信网关转发链首地址或直连地址，并限制审计字段长度。 */
    private String clientIp(HttpServletRequest request) {
        if (request == null) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = StringUtils.hasText(forwarded)
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        if (!validText(value, 64)) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        return value;
    }

    private String userAgent(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("User-Agent");
        if (!StringUtils.hasText(value)) return "UNKNOWN";
        value = value.trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void requireBatchNo(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("SB\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void requireReversalNo(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("SRO\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }
}
