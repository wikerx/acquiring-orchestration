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
 * 管理端邮件发送记录 HTTP 控制器，用于运营后台，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 */
@RestController
@RequestMapping("/admin/email/records")
public class AdminEmailRecordController {

    private final AdminEmailApplicationService emailApplicationService;

    /**
     * 创建邮件发送记录查询控制器。
     *
     * @param emailApplicationService 邮件管理应用服务
     */
    public AdminEmailRecordController(AdminEmailApplicationService emailApplicationService) {
        this.emailApplicationService = emailApplicationService;
    }

    /**
     * 分页查询邮件发送记录，收件地址按展示规则处理。
     *
     * @param query 模板、收件地址、状态和时间范围等可选条件
     * @return 邮件发送记录分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("email:record:list")
    public CommonResult<PageResult<EmailRecordResponse>> pageRecords(@RequestBody(required = false) EmailRecordQuery query) {
        return success(emailApplicationService.pageRecords(query));
    }

    /**
     * 查询指定邮件发送记录及失败摘要。
     *
     * @param id 邮件发送记录主键
     * @return 邮件发送记录详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("email:record:detail")
    public CommonResult<EmailRecordResponse> getRecord(@PathVariable("id") Long id) {
        return success(emailApplicationService.getRecord(id));
    }

    /**
     * 基于历史记录重新发送邮件，并创建新的发送尝试。
     *
     * @param id 原邮件发送记录主键
     * @return 本次重发结果
     */
    @PostMapping("/{id}/resend")
    @RequiresPermission("email:record:resend")
    @OperationLog(moduleName = "邮件发送记录", businessType = OperationTypeConstants.UPDATE, operation = "重新发送邮件")
    public CommonResult<EmailSendResult> resend(@PathVariable("id") Long id) {
        return success(emailApplicationService.resend(id));
    }
}
