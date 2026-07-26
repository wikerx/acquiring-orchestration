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

@RestController
@RequestMapping("/admin/email/records")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailRecordController
 * @date : 2026-07-04 16:11
 * @email : scott_x@163.com
 * @description : AdminEmailRecordController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminEmailRecordController {

    /**
     * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private final AdminEmailApplicationService emailApplicationService;

    /**
     * 创建 AdminEmailRecordController 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminEmailRecordController 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param emailApplicationService email Application Service 输入值，含义由调用方法名称和所属业务对象限定
     */
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
