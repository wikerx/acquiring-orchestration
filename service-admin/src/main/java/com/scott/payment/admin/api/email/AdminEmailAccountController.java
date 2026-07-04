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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailAccountController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 邮件管理Admin Email Account 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/email/accounts")
public class AdminEmailAccountController {

    /**
     * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private final AdminEmailApplicationService emailApplicationService;

    public AdminEmailAccountController(AdminEmailApplicationService emailApplicationService) {
        this.emailApplicationService = emailApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("email:account:list")
    public CommonResult<PageResult<EmailAccountResponse>> pageAccounts(@RequestBody(required = false) EmailAccountQuery query) {
        return success(emailApplicationService.pageAccounts(query));
    }

    /**
     * 获取邮件管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{id}")
    @RequiresPermission("email:account:detail")
    public CommonResult<EmailAccountResponse> getAccount(@PathVariable("id") Long id) {
        return success(emailApplicationService.getAccount(id));
    }

    /**
     * 创建或保存邮件管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    @RequiresPermission("email:account:add")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.CREATE, operation = "新增邮件发件账户")
    public CommonResult<EmailAccountResponse> createAccount(@Valid @RequestBody EmailAccountSaveRequest request) {
        return success(emailApplicationService.createAccount(request));
    }

    /**
     * 更新邮件管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}")
    @RequiresPermission("email:account:edit")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.UPDATE, operation = "修改邮件发件账户")
    public CommonResult<EmailAccountResponse> updateAccount(@PathVariable("id") Long id,
                                                            @Valid @RequestBody EmailAccountSaveRequest request) {
        return success(emailApplicationService.updateAccount(id, request));
    }

    /**
     * 更新邮件管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("email:account:status")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.UPDATE, operation = "切换邮件发件账户状态")
    public CommonResult<EmailAccountResponse> updateAccountStatus(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody EmailStatusRequest request) {
        return success(emailApplicationService.updateAccountStatus(id, request.getStatus()));
    }

    /**
     * 执行邮件管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}/default")
    @RequiresPermission("email:account:default")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.UPDATE, operation = "设置默认邮件发件账户")
    public CommonResult<EmailAccountResponse> setDefaultAccount(@PathVariable("id") Long id) {
        return success(emailApplicationService.setDefaultAccount(id));
    }

    /**
     * 发送邮件管理消息或外部请求，并记录必要的执行结果。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/{id}/test")
    @RequiresPermission("email:account:test")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.UPDATE, operation = "测试发送邮件")
    public CommonResult<EmailSendResult> sendTestEmail(@PathVariable("id") Long id,
                                                       @Valid @RequestBody EmailAccountTestRequest request) {
        return success(emailApplicationService.sendTestEmail(id, request));
    }

    /**
     * 删除邮件管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("email:account:remove")
    @OperationLog(moduleName = "发件账户配置", businessType = OperationTypeConstants.DELETE, operation = "删除邮件发件账户")
    public CommonResult<Void> deleteAccount(@PathVariable("id") Long id) {
        emailApplicationService.deleteAccount(id);
        return success();
    }
}
