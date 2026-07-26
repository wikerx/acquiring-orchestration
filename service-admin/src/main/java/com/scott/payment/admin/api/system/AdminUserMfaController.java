package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminUserMfaApplicationService;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaActionRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaExemptRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogQuery;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogResponse;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaStatusResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserMfaController
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 后台用户 MFA 管理接口，位于 service-admin 接口层；负责多因素认证管理操作的 HTTP 映射、权限校验和参数接收。
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/users/mfa")
public class AdminUserMfaController {

    /**
     * admin User Mfa Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminUserMfaApplicationService adminUserMfaApplicationService;

    /**
     * 创建后台用户 MFA 控制器。
     *
     * @param adminUserMfaApplicationService MFA 应用服务
     */
    public AdminUserMfaController(AdminUserMfaApplicationService adminUserMfaApplicationService) {
        this.adminUserMfaApplicationService = adminUserMfaApplicationService;
    }

    @PostMapping("/require")
    @RequiresPermission("sys:user:mfa:require")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "强制启用用户 MFA", recordRequest = false, recordResponse = false)
    /**
     * 强制校验 require Mfa 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<UserMfaStatusResponse> requireMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.requireMfa(request));
    }

    @PostMapping("/reset")
    @RequiresPermission("sys:user:mfa:reset")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重置用户 MFA", recordRequest = false, recordResponse = false)
    /**
     * 完成 reset Mfa 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<UserMfaStatusResponse> resetMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.resetMfa(request));
    }

    @PostMapping("/exempt")
    @RequiresPermission("sys:user:mfa:exempt")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "配置用户 MFA 豁免", recordRequest = false, recordResponse = false)
    /**
     * 完成 exempt Mfa 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<UserMfaStatusResponse> exemptMfa(@Valid @RequestBody UserMfaExemptRequest request) {
        return success(adminUserMfaApplicationService.exemptMfa(request));
    }

    @PostMapping("/disable")
    @RequiresPermission("sys:user:mfa:disable")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "停用用户 MFA", recordRequest = false, recordResponse = false)
    /**
     * 完成 disable Mfa 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<UserMfaStatusResponse> disableMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.disableMfa(request));
    }

    @PostMapping("/unlock")
    @RequiresPermission("sys:user:mfa:unlock")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "解锁用户 MFA", recordRequest = false, recordResponse = false)
    /**
     * 完成 unlock Mfa 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<UserMfaStatusResponse> unlockMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.unlockMfa(request));
    }

    @PostMapping("/resend-bind-mail")
    @RequiresPermission("sys:user:mfa:resend")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重发 MFA 绑定邮件", recordRequest = false, recordResponse = false)
    /**
     * 完成 resend Bind Mail 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<UserMfaStatusResponse> resendBindMail(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.resendBindMail(request));
    }

    @PostMapping("/logs/search")
    @RequiresPermission("sys:user:mfa:log")
    /**
     * 完成 page Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PageResult<UserMfaLogResponse>> pageLogs(@RequestBody(required = false) UserMfaLogQuery query) {
        return success(adminUserMfaApplicationService.pageLogs(query));
    }
}
