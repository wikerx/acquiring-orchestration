package com.scott.payment.admin.api.email;

import com.scott.payment.admin.application.email.AdminEmailApplicationService;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountSaveRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountTestRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailStatusRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/admin/email/accounts")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailAccountController
 * @date : 2026-07-04 16:11
 * @email : scott_x@163.com
 * @description : AdminEmailAccountController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminEmailAccountController {

    /**
     * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private final AdminEmailApplicationService emailApplicationService;

    /**
     * 创建 AdminEmailAccountController 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminEmailAccountController 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param emailApplicationService email Application Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminEmailAccountController(AdminEmailApplicationService emailApplicationService) {
        this.emailApplicationService = emailApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("email:account:list")
    public CommonResult<PageResult<EmailAccountResponse>> pageAccounts(@RequestBody(required = false) EmailAccountQuery query) {
        return success(emailApplicationService.pageAccounts(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("email:account:detail")
    public CommonResult<EmailAccountResponse> getAccount(@PathVariable("id") Long id) {
        return success(emailApplicationService.getAccount(id));
    }

    @PostMapping
    @RequiresPermission("email:account:add")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.CREATE, operation = "新增邮件发件账户")
    public CommonResult<EmailAccountResponse> createAccount(@Valid @RequestBody EmailAccountSaveRequest request) {
        return success(emailApplicationService.createAccount(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("email:account:edit")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.UPDATE, operation = "修改邮件发件账户")
    public CommonResult<EmailAccountResponse> updateAccount(@PathVariable("id") Long id,
                                                            @Valid @RequestBody EmailAccountSaveRequest request) {
        return success(emailApplicationService.updateAccount(id, request));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("email:account:status")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.UPDATE, operation = "切换邮件发件账户状态")
    public CommonResult<EmailAccountResponse> updateAccountStatus(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody EmailStatusRequest request) {
        return success(emailApplicationService.updateAccountStatus(id, request.getStatus()));
    }

    @PutMapping("/{id}/default")
    @RequiresPermission("email:account:default")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.UPDATE, operation = "设置默认邮件发件账户")
    public CommonResult<EmailAccountResponse> setDefaultAccount(@PathVariable("id") Long id) {
        return success(emailApplicationService.setDefaultAccount(id));
    }

    @PostMapping("/{id}/test")
    @RequiresPermission("email:account:test")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.UPDATE, operation = "测试发送邮件")
    public CommonResult<EmailSendResult> sendTestEmail(@PathVariable("id") Long id,
                                                       @Valid @RequestBody EmailAccountTestRequest request) {
        return success(emailApplicationService.sendTestEmail(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("email:account:remove")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.DELETE, operation = "删除邮件发件账户")
    public CommonResult<Void> deleteAccount(@PathVariable("id") Long id) {
        emailApplicationService.deleteAccount(id);
        return success();
    }
}
