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
 * 管理后台邮件模板接口。
 */
@RestController
@RequestMapping("/admin/email/templates")
public class AdminEmailTemplateController {

    private final AdminEmailApplicationService emailApplicationService;

    public AdminEmailTemplateController(AdminEmailApplicationService emailApplicationService) {
        this.emailApplicationService = emailApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("email:template:list")
    public CommonResult<PageResult<EmailTemplateResponse>> pageTemplates(@RequestBody(required = false) EmailTemplateQuery query) {
        return success(emailApplicationService.pageTemplates(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("email:template:detail")
    public CommonResult<EmailTemplateResponse> getTemplate(@PathVariable("id") Long id) {
        return success(emailApplicationService.getTemplate(id));
    }

    @PostMapping
    @RequiresPermission("email:template:add")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.CREATE, operation = "新增邮件模板")
    public CommonResult<EmailTemplateResponse> createTemplate(@Valid @RequestBody EmailTemplateSaveRequest request) {
        return success(emailApplicationService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("email:template:edit")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.UPDATE, operation = "修改邮件模板")
    public CommonResult<EmailTemplateResponse> updateTemplate(@PathVariable("id") Long id,
                                                              @Valid @RequestBody EmailTemplateSaveRequest request) {
        return success(emailApplicationService.updateTemplate(id, request));
    }

    @PostMapping("/{id}/copy")
    @RequiresPermission("email:template:copy")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.CREATE, operation = "复制邮件模板")
    public CommonResult<EmailTemplateResponse> copyTemplate(@PathVariable("id") Long id) {
        return success(emailApplicationService.copyTemplate(id));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("email:template:status")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.UPDATE, operation = "切换邮件模板状态")
    public CommonResult<EmailTemplateResponse> updateTemplateStatus(@PathVariable("id") Long id,
                                                                    @Valid @RequestBody EmailStatusRequest request) {
        return success(emailApplicationService.updateTemplateStatus(id, request.getStatus()));
    }

    @PostMapping("/preview")
    @RequiresPermission("email:template:preview")
    public CommonResult<EmailTemplatePreviewResponse> previewTemplate(@Valid @RequestBody EmailTemplatePreviewRequest request) {
        return success(emailApplicationService.previewTemplate(request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("email:template:remove")
    @OperationLog(moduleName = "邮件模板管理", businessType = OperationTypeConstants.DELETE, operation = "删除邮件模板")
    public CommonResult<Void> deleteTemplate(@PathVariable("id") Long id) {
        emailApplicationService.deleteTemplate(id);
        return success();
    }
}
