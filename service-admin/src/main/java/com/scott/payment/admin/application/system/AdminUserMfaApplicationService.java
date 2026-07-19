package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaActionRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaExemptRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogQuery;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogResponse;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaStatusResponse;
import com.scott.payment.admin.service.AdminUserMfaService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserMfaApplicationService
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 后台用户 MFA 应用服务，位于 service-admin 应用编排层；承接用户管理页面的 OTP 管理用例并调用领域服务。
 * @status : create
 */
@Service
public class AdminUserMfaApplicationService {

    private final AdminUserMfaService adminUserMfaService;

    /**
     * 创建后台用户 MFA 应用服务。
     *
     * @param adminUserMfaService MFA 领域服务
     */
    public AdminUserMfaApplicationService(AdminUserMfaService adminUserMfaService) {
        this.adminUserMfaService = adminUserMfaService;
    }

    public UserMfaStatusResponse requireMfa(UserMfaActionRequest request) {
        return adminUserMfaService.requireMfa(request);
    }

    public UserMfaStatusResponse resetMfa(UserMfaActionRequest request) {
        return adminUserMfaService.resetMfa(request);
    }

    public UserMfaStatusResponse exemptMfa(UserMfaExemptRequest request) {
        return adminUserMfaService.exemptMfa(request);
    }

    public UserMfaStatusResponse disableMfa(UserMfaActionRequest request) {
        return adminUserMfaService.disableMfa(request);
    }

    public UserMfaStatusResponse unlockMfa(UserMfaActionRequest request) {
        return adminUserMfaService.unlockMfa(request);
    }

    public UserMfaStatusResponse resendBindMail(UserMfaActionRequest request) {
        return adminUserMfaService.resendBindMail(request);
    }

    public PageResult<UserMfaLogResponse> pageLogs(UserMfaLogQuery query) {
        return adminUserMfaService.pageLogs(query);
    }
}
