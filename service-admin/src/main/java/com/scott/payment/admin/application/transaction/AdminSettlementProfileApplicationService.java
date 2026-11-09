package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.application.auth.AdminClientIpResolver;
import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalProfileCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalProfileVersionCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileUpdateRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementProfileService;
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
 * Admin 结算档案管理编排；登录主体只从内部认证上下文解析。
 */
@Service
public class AdminSettlementProfileApplicationService {

    private final AdminSettlementProfileService profileService;
    private final AdminMerchantDataScopeResolver dataScopeResolver;
    private final SettlementInternalClient settlementInternalClient;

    /**
     * 创建管理端结算配置应用服务。
     */
    public AdminSettlementProfileApplicationService(AdminSettlementProfileService profileService,
                                                     AdminMerchantDataScopeResolver dataScopeResolver,
                                                     SettlementInternalClient settlementInternalClient) {
        this.profileService = profileService;
        this.dataScopeResolver = dataScopeResolver;
        this.settlementInternalClient = settlementInternalClient;
    }

    /**
     * 查询当前账号可见的结算档案。
     */
    public PageResult<ProfileSummary> search(ProfileSearchRequest request) {
        InternalAuthAccount account = currentAdminAccount();
        return profileService.search(request, dataScopeResolver.resolve(account));
    }

    /**
     * 查询当前账号可见的单个结算档案。
     */
    public ProfileSummary detail(String settlementProfileNo) {
        InternalAuthAccount account = currentAdminAccount();
        return profileService.detail(settlementProfileNo, dataScopeResolver.resolve(account));
    }

    /**
     * 在当前账号商户范围内创建首个活动结算档案。
     *
     * @param request 账户、币种、日切配置、原因和请求幂等键
     * @param servletRequest 可信客户端环境
     * @return service-settlement 提交后从主库读取的完整运营视图
     */
    public ProfileSummary create(ProfileCreateRequest request, HttpServletRequest servletRequest) {
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        InternalProfileCreateRequest command = createCommand(request, account, servletRequest);
        requireMerchantAccess(command.getMerchantId(), scope);
        ProfileCommandResponse result = settlementInternalClient.createProfile(command);
        return profileService.detail(result.getSettlementProfileNo(), scope);
    }

    /**
     * 校验当前账号数据范围后，通过结算服务创建新的活动档案版本。
     *
     * @param settlementProfileNo 当前活动档案号
     * @param request 新调度参数、原因、请求幂等键和期望版本
     * @param servletRequest 可信客户端环境
     * @return 新活动档案的完整运营视图
     */
    public ProfileSummary update(String settlementProfileNo,
                                 ProfileUpdateRequest request,
                                 HttpServletRequest servletRequest) {
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        profileService.detail(settlementProfileNo, scope);
        InternalProfileVersionCreateRequest command = versionCommand(request, account, servletRequest);
        ProfileCommandResponse result = settlementInternalClient.createProfileVersion(
                settlementProfileNo, command);
        return profileService.detail(result.getSettlementProfileNo(), scope);
    }

    /**
     * 将浏览器创建请求转换为仅内部可见的可信命令。
     */
    private InternalProfileCreateRequest createCommand(ProfileCreateRequest request,
                                                       InternalAuthAccount account,
                                                       HttpServletRequest servletRequest) {
        if (request == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalProfileCreateRequest command = new InternalProfileCreateRequest();
        command.setRequestKey(request.getRequestKey());
        command.setMerchantId(request.getMerchantId());
        command.setSettlementAccountId(request.getSettlementAccountId());
        command.setTargetCurrency(request.getTargetCurrency());
        command.setBusinessTimeZone(request.getBusinessTimeZone());
        command.setDailyCutoffTime(request.getDailyCutoffTime());
        command.setProcessingMode(request.getProcessingMode());
        command.setReason(request.getReason());
        enrichOperator(command, account, servletRequest);
        return command;
    }

    /**
     * 将浏览器编辑请求转换为仅内部可见的版本切换命令。
     */
    private InternalProfileVersionCreateRequest versionCommand(ProfileUpdateRequest request,
                                                               InternalAuthAccount account,
                                                               HttpServletRequest servletRequest) {
        if (request == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalProfileVersionCreateRequest command = new InternalProfileVersionCreateRequest();
        command.setRequestKey(request.getRequestKey());
        command.setExpectedVersion(request.getExpectedVersion());
        command.setBusinessTimeZone(request.getBusinessTimeZone());
        command.setDailyCutoffTime(request.getDailyCutoffTime());
        command.setProcessingMode(request.getProcessingMode());
        command.setReason(request.getReason());
        enrichOperator(command, account, servletRequest);
        return command;
    }

    /**
     * 注入初始档案命令的可信操作审计。
     */
    private void enrichOperator(InternalProfileCreateRequest command,
                                InternalAuthAccount account,
                                HttpServletRequest servletRequest) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(servletRequest));
        command.setUserAgent(userAgent(servletRequest));
        command.setOperationTime(LocalDateTime.now());
    }

    /**
     * 注入档案版本命令的可信操作审计。
     */
    private void enrichOperator(InternalProfileVersionCreateRequest command,
                                InternalAuthAccount account,
                                HttpServletRequest servletRequest) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(servletRequest));
        command.setUserAgent(userAgent(servletRequest));
        command.setOperationTime(LocalDateTime.now());
    }

    /**
     * 校验创建命令中的商户属于当前管理账号数据范围。
     */
    private void requireMerchantAccess(String merchantId, AdminMerchantDataScope scope) {
        if (!StringUtils.hasText(merchantId) || scope == null || scope.empty()
                || (!scope.allMerchants() && !scope.merchantIds().contains(merchantId.trim()))) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户不存在或不在当前数据范围");
        }
    }

    /**
     * 构造排序稳定的角色权限快照。
     */
    private String roleSnapshot(InternalAuthAccount account) {
        String snapshot = account.getRoles() == null ? "" : account.getRoles().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.joining(","));
        return snapshot.isBlank() ? "UNASSIGNED" : snapshot;
    }

    /**
     * 从可信代理链解析客户端 IP。
     */
    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        String value = AdminClientIpResolver.resolve(request);
        if (!StringUtils.hasText(value) || value.length() > 64) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return value;
    }

    /**
     * 提取并限制客户端 User-Agent 审计字段。
     */
    private String userAgent(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("User-Agent");
        if (!StringUtils.hasText(value)) {
            return "UNKNOWN";
        }
        String normalized = value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    /**
     * 从可信认证上下文生成操作人显示名称。
     */
    private String operatorName(InternalAuthAccount account) {
        String name = StringUtils.hasText(account.getRealName())
                ? account.getRealName().trim()
                : StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : null;
        if (!StringUtils.hasText(name) || name.length() > 128) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return name;
    }

    /**
     * 读取并校验当前管理端认证账户。
     */
    private InternalAuthAccount currentAdminAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAppId() == null || account.getAccountId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }
}
