package com.scott.payment.admin.api.email;

import com.scott.payment.admin.application.email.AdminEmailApplicationService;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台邮件发送记录接口。
 */
@RestController
@RequestMapping("/admin/email/records")
public class AdminEmailRecordController {

    private final AdminEmailApplicationService emailApplicationService;

    public AdminEmailRecordController(AdminEmailApplicationService emailApplicationService) {
        this.emailApplicationService = emailApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("email:record:list")
    public CommonResult<PageResult<EmailRecordResponse>> pageRecords(@RequestBody(required = false) EmailRecordQuery query) {
        return success(emailApplicationService.pageRecords(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("email:record:detail")
    public CommonResult<EmailRecordResponse> getRecord(@PathVariable("id") Long id) {
        return success(emailApplicationService.getRecord(id));
    }

    @PostMapping("/{id}/resend")
    @RequiresPermission("email:record:resend")
    @OperationLog(moduleName = "邮件发送记录", businessType = OperationTypeConstants.UPDATE, operation = "重新发送邮件")
    public CommonResult<EmailSendResult> resend(@PathVariable("id") Long id) {
        return success(emailApplicationService.resend(id));
    }
}
