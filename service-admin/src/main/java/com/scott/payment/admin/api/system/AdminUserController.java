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
/**
 * 完成 export Users 分支的校验或状态更新。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @param response response 输入值，含义由调用方法名称和所属业务对象限定
 */
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
     * 完成 create User 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
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
     * 写入或更新 update User 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
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
     * 写入或更新 update Status 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 reset Password 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 user Roles 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 grant Roles 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 delete Users 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param accountIds account Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
