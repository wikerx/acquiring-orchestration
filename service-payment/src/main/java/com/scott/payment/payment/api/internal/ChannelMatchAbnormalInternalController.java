package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payment.application.ChannelMatchAbnormalApplicationService;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AssignCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;
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
 * @classname : ChannelMatchAbnormalInternalController
 * @date : 2026-08-06 00:00
 * @description : Payment 勾兑异常内部接口，仅供受签名保护的 Admin 服务调用，不注册人工交易终态修正路由。
 * @status : create
 */
@RestController
@RequestMapping("/internal/payment/channel-match/abnormalities")
public class ChannelMatchAbnormalInternalController {

    private final ChannelMatchAbnormalApplicationService applicationService;

    /** @param applicationService 勾兑异常应用服务 */
    public ChannelMatchAbnormalInternalController(ChannelMatchAbnormalApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询案件分页及统计。 */
    @PostMapping("/search")
    public CommonResult<AbnormalSearchResponse> search(@RequestBody(required = false) AbnormalQuery query) {
        return success(applicationService.search(query));
    }

    /** 查询案件聚合详情。 */
    @GetMapping("/{eventId}")
    public CommonResult<AbnormalDetailResponse> detail(
            @PathVariable("eventId") String eventId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.detail(eventId, transactionDateTime));
    }

    /** 领取或转派案件。 */
    @PostMapping("/{eventId}/claim")
    public CommonResult<AbnormalRecord> assign(@PathVariable("eventId") String eventId,
                                               @RequestBody AssignCommand command) {
        return success(applicationService.assign(eventId, command));
    }

    /** 使用案件分片时间重新查询渠道。 */
    @PostMapping("/{eventId}/requery")
    public CommonResult<AbnormalRecord> requery(@PathVariable("eventId") String eventId,
                                                @RequestBody RequeryCommand command) {
        return success(applicationService.requery(eventId, command));
    }

    /** 批量重新查询渠道，单批最多 100 笔。 */
    @PostMapping("/batch-requery")
    public CommonResult<BatchRequeryResult> batchRequery(@RequestBody BatchRequeryCommand command) {
        return success(applicationService.batchRequery(command));
    }

    /** 确认无需修改或忽略案件。 */
    @PostMapping("/{eventId}/resolve")
    public CommonResult<AbnormalRecord> resolve(@PathVariable("eventId") String eventId,
                                                @RequestBody ResolveCommand command) {
        return success(applicationService.resolve(eventId, command));
    }
}
