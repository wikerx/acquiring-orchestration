package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminUserApplicationService;
import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountResetPasswordRequest;
import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserController
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台用户管理控制器
 * @status : create
 *
 * <p>Controller 仅接收参数、校验权限并调用
 * {@link AdminUserApplicationService}，用户、角色和密码规则均下沉到应用层及领域服务。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Admin User 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/users")
public class AdminUserController {

    /**
     * 后台用户应用服务。
     */
    private final AdminUserApplicationService adminUserApplicationService;

    /**
     * 创建后台用户控制器。
     *
     * @param adminUserApplicationService 后台用户应用服务
     */
    public AdminUserController(AdminUserApplicationService adminUserApplicationService) {
        this.adminUserApplicationService = adminUserApplicationService;
    }

    /**
     * 分页查询后台用户。
     *
     * @param request 查询条件
     * @return 用户分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("system:user:list")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询后台用户列表")
    public CommonResult<PageResult<SysUserAccountDTO>> listUsers(@RequestBody(required = false) SysUserAccountQueryRequest request) {
        return success(adminUserApplicationService.pageUsers(request));
    }

    /**
     * 导出后台用户列表。
     *
     * @param request 查询条件
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("system:user:export")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.EXPORT,
            operation = "导出后台用户列表")
    public void exportUsers(@RequestBody(required = false) SysUserAccountQueryRequest request,
                            HttpServletResponse response) {
        adminUserApplicationService.exportUsers(request, currentOperatorName(), response);
    }

    /**
     * 新增后台用户。
     *
     * @param request 新增请求
     * @return 新增后的用户
     */
    @PostMapping("/create")
    @RequiresPermission("system:user:add")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.CREATE,
            operation = "新增后台用户", recordRequest = false, recordResponse = false)
    /**
     * 创建或保存系统管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<SysUserAccountDTO> createUser(@Valid @RequestBody SysUserAccountCreateRequest request) {
        return success(adminUserApplicationService.createUser(request));
    }

    /**
     * 编辑后台用户。
     *
     * @param request 更新请求
     * @return 更新后的用户
     */
    @PostMapping("/update")
    @RequiresPermission("system:user:edit")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "编辑后台用户", recordRequest = false, recordResponse = false)
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<SysUserAccountDTO> updateUser(@Valid @RequestBody SysUserAccountUpdateRequest request) {
        return success(adminUserApplicationService.updateUser(request));
    }

    /**
     * 更新后台用户状态。
     *
     * @param request 状态更新请求
     * @return 空响应
     */
    @PostMapping("/status")
    @RequiresPermission("system:user:changeStatus")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "更新后台用户状态", recordRequest = false, recordResponse = false)
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> updateStatus(@Valid @RequestBody SysUserAccountStatusRequest request) {
        adminUserApplicationService.updateStatus(request);
        return success();
    }

    /**
     * 重置后台用户密码。
     *
     * @param request 重置密码请求
     * @return 空响应
     */
    @PostMapping("/reset-password")
    @RequiresPermission("system:user:resetPwd")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重置后台用户密码", recordRequest = false, recordResponse = false)
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> resetPassword(@Valid @RequestBody SysUserAccountResetPasswordRequest request) {
        adminUserApplicationService.resetPassword(request);
        return success();
    }

    /**
     * 查询后台用户角色授权。
     *
     * @param request 用户角色请求
     * @return 用户角色授权信息
     */
    @PostMapping("/roles")
    @RequiresPermission("system:user:assign-role")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.QUERY,
            operation = "查询后台用户角色授权")
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<SysUserRoleAuthDTO> userRoles(@Valid @RequestBody SysUserRoleGrantRequest request) {
        return success(adminUserApplicationService.userRoles(request.getAccountId()));
    }

    /**
     * 保存后台用户角色授权。
     *
     * @param request 角色授权请求
     * @return 空响应
     */
    @PostMapping("/roles/grant")
    @RequiresPermission("system:user:assign-role")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "分配后台用户角色", recordRequest = false, recordResponse = false)
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> grantRoles(@Valid @RequestBody SysUserRoleGrantRequest request) {
        adminUserApplicationService.grantRoles(request);
        return success();
    }

    /**
     * 删除后台用户。
     *
     * @param accountIds 账号主键列表
     * @return 空响应
     */
    @PostMapping("/delete")
    @RequiresPermission("system:user:remove")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.DELETE,
            operation = "删除后台用户", recordRequest = false, recordResponse = false)
    /**
     * 删除系统管理数据，按业务规则处理引用校验和删除边界。
     * @param accountIds 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> deleteUsers(@RequestBody List<Long> accountIds) {
        adminUserApplicationService.removeUsers(accountIds);
        return success();
    }

    /**
     * 获取当前操作人名称。
     *
     * @return 操作人名称
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
