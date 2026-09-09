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
 * @description : admin用户MFA应用服务，位于 运营后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
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

    /**
     * 将指定后台账号设置为强制使用 MFA，并返回最新状态。
     * <p>
     * 校验失败时按 运营后台服务 统一异常语义中断流程，不返回部分校验结果。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 更新后的后台账号 MFA 状态
     */
    public UserMfaStatusResponse requireMfa(UserMfaActionRequest request) {
        return adminUserMfaService.requireMfa(request);
    }

    /**
     * 更新多因子认证，保持业务状态、配置项或展示字段与请求意图一致。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 重置后的后台账号 MFA 状态
     */
    public UserMfaStatusResponse resetMfa(UserMfaActionRequest request) {
        return adminUserMfaService.resetMfa(request);
    }

    /**
     * 为后台账号 MFA 设置受控豁免，并保留操作原因和审计上下文。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 豁免后的后台账号 MFA 状态
     */
    public UserMfaStatusResponse exemptMfa(UserMfaExemptRequest request) {
        return adminUserMfaService.exemptMfa(request);
    }

    /**
     * 停用后台账号 MFA，并使现有绑定不再参与登录校验。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 停用后的后台账号 MFA 状态
     */
    public UserMfaStatusResponse disableMfa(UserMfaActionRequest request) {
        return adminUserMfaService.disableMfa(request);
    }

    /**
     * 解除后台账号 MFA 锁定状态，恢复符合条件的后续登录操作。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 解锁后的后台账号 MFA 状态
     */
    public UserMfaStatusResponse unlockMfa(UserMfaActionRequest request) {
        return adminUserMfaService.unlockMfa(request);
    }

    /**
     * 重新发送后台账号 MFA 绑定邮件，并记录当前操作人审计信息。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 重新发送后的后台账号 MFA 状态
     */
    public UserMfaStatusResponse resendBindMail(UserMfaActionRequest request) {
        return adminUserMfaService.resendBindMail(request);
    }

    /**
     * 查询日志；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<UserMfaLogResponse> pageLogs(UserMfaLogQuery query) {
        return adminUserMfaService.pageLogs(query);
    }
}
