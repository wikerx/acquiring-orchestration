package com.scott.payment.admin.api.email;

import com.scott.payment.admin.application.email.AdminEmailApplicationService;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailStatusRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateSaveRequest;
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
 * @classname : AdminEmailTemplateController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 邮件管理Admin Email Template 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/email/templates")
public class AdminEmailTemplateController {

    /**
     * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private final AdminEmailApplicationService emailApplicationService;

    public AdminEmailTemplateController(AdminEmailApplicationService emailApplicationService) {
        this.emailApplicationService = emailApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("email:template:list")
    public CommonResult<PageResult<EmailTemplateResponse>> pageTemplates(@RequestBody(required = false) EmailTemplateQuery query) {
        return success(emailApplicationService.pageTemplates(query));
    }

    /**
     * 获取邮件管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{id}")
    @RequiresPermission("email:template:detail")
    public CommonResult<EmailTemplateResponse> getTemplate(@PathVariable("id") Long id) {
        return success(emailApplicationService.getTemplate(id));
    }

    /**
     * 创建或保存邮件管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    @RequiresPermission("email:template:add")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.CREATE, operation = "新增邮件模板")
    public CommonResult<EmailTemplateResponse> createTemplate(@Valid @RequestBody EmailTemplateSaveRequest request) {
        return success(emailApplicationService.createTemplate(request));
    }

    /**
     * 更新邮件管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}")
    @RequiresPermission("email:template:edit")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.UPDATE, operation = "修改邮件模板")
    public CommonResult<EmailTemplateResponse> updateTemplate(@PathVariable("id") Long id,
                                                              @Valid @RequestBody EmailTemplateSaveRequest request) {
        return success(emailApplicationService.updateTemplate(id, request));
    }

    /**
     * 执行邮件管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/{id}/copy")
    @RequiresPermission("email:template:copy")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.CREATE, operation = "复制邮件模板")
    public CommonResult<EmailTemplateResponse> copyTemplate(@PathVariable("id") Long id) {
        return success(emailApplicationService.copyTemplate(id));
    }

    /**
     * 更新邮件管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("email:template:status")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.UPDATE, operation = "切换邮件模板状态")
    public CommonResult<EmailTemplateResponse> updateTemplateStatus(@PathVariable("id") Long id,
                                                                    @Valid @RequestBody EmailStatusRequest request) {
        return success(emailApplicationService.updateTemplateStatus(id, request.getStatus()));
    }

    /**
     * 执行邮件管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/preview")
    @RequiresPermission("email:template:preview")
    public CommonResult<EmailTemplatePreviewResponse> previewTemplate(@Valid @RequestBody EmailTemplatePreviewRequest request) {
        return success(emailApplicationService.previewTemplate(request));
    }

    /**
     * 删除邮件管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("email:template:remove")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.DELETE, operation = "删除邮件模板")
    public CommonResult<Void> deleteTemplate(@PathVariable("id") Long id) {
        emailApplicationService.deleteTemplate(id);
        return success();
    }
}
