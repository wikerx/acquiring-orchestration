package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminChannelMatchAbnormalApplicationService;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AssignRequest;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.ResolveCommand;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelMatchAbnormalController
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 管理端勾兑异常接口，提供查询和受控案件处置；不注册 repair 或人工确认交易成功接口。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions/channel-match-abnormalities")
public class AdminChannelMatchAbnormalController {

    private final AdminChannelMatchAbnormalApplicationService applicationService;

    /** @param applicationService Admin 勾兑异常应用服务 */
    public AdminChannelMatchAbnormalController(AdminChannelMatchAbnormalApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询案件分页和统计。 */
    @PostMapping("/search")
    @RequiresPermission("transaction:match-abnormal:list")
    @OperationLog(moduleName = "勾兑异常交易", businessType = OperationTypeConstants.QUERY, operation = "查询勾兑异常")
    public CommonResult<AbnormalSearchResponse> search(@RequestBody(required = false) AbnormalQuery query) {
        return success(applicationService.search(query));
    }

    /** 查询案件聚合详情。 */
    @GetMapping("/{eventId}")
    @RequiresPermission("transaction:match-abnormal:detail")
    @OperationLog(moduleName = "勾兑异常交易", businessType = OperationTypeConstants.QUERY, operation = "查询异常详情")
    public CommonResult<AbnormalDetailResponse> detail(
            @PathVariable("eventId") String eventId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.detail(eventId, transactionDateTime));
    }

    /** 导出脱敏案件列表。 */
    @PostMapping("/export")
    @RequiresPermission("transaction:match-abnormal:export")
    @OperationLog(moduleName = "勾兑异常交易", businessType = OperationTypeConstants.EXPORT, operation = "导出勾兑异常")
    public void export(@RequestBody(required = false) AbnormalQuery query, HttpServletResponse response) {
        applicationService.export(query, response);
    }

    /** 领取或转派案件。 */
    @PostMapping("/{eventId}/claim")
    @RequiresPermission("transaction:match-abnormal:assign")
    @OperationLog(moduleName = "勾兑异常交易", businessType = OperationTypeConstants.UPDATE, operation = "领取或转派异常")
    public CommonResult<AbnormalRecord> assign(@PathVariable("eventId") String eventId,
                                               @RequestBody AssignRequest request) {
        return success(applicationService.assign(eventId, request));
    }

    /** 单笔重新勾兑。 */
    @PostMapping("/{eventId}/requery")
    @RequiresPermission("transaction:match-abnormal:requery")
    @OperationLog(moduleName = "勾兑异常交易", businessType = OperationTypeConstants.UPDATE, operation = "重新勾兑")
    public CommonResult<AbnormalRecord> requery(@PathVariable("eventId") String eventId,
                                                @RequestBody RequeryCommand command) {
        return success(applicationService.requery(eventId, command));
    }

    /** 批量重新勾兑。 */
    @PostMapping("/batch-requery")
    @RequiresPermission("transaction:match-abnormal:batch-requery")
    @OperationLog(moduleName = "勾兑异常交易", businessType = OperationTypeConstants.UPDATE, operation = "批量重新勾兑")
    public CommonResult<BatchRequeryResult> batchRequery(@RequestBody BatchRequeryCommand command) {
        return success(applicationService.batchRequery(command));
    }

    /** 确认无需修改或忽略案件。 */
    @PostMapping("/{eventId}/resolve")
    @RequiresPermission("transaction:match-abnormal:resolve")
    @OperationLog(moduleName = "勾兑异常交易", businessType = OperationTypeConstants.UPDATE, operation = "处置勾兑异常")
    public CommonResult<AbnormalRecord> resolve(@PathVariable("eventId") String eventId,
                                                @RequestBody ResolveCommand command) {
        return success(applicationService.resolve(eventId, command));
    }
}
