package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payment.application.ChannelMatchAbnormalApplicationService;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AssignCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalInternalController
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : Payment 勾兑异常内部命令接口，仅供受签名保护的 Admin 服务提交领取、重查和非资金终态处置命令。
 * @status : create
 */
@RestController
@RequestMapping("/internal/payment/channel-match/abnormalities")
public class ChannelMatchAbnormalInternalController {

    private final ChannelMatchAbnormalApplicationService applicationService;

    /**
     * 创建勾兑异常内部命令接口。
     *
     * @param applicationService 勾兑异常命令应用服务
     */
    public ChannelMatchAbnormalInternalController(ChannelMatchAbnormalApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 领取或转派活动案件。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间、期望版本和可信操作人
     * @return 领取或转派后的最新案件记录
     */
    @PostMapping("/{eventId}/claim")
    public CommonResult<AbnormalRecord> assign(@PathVariable("eventId") String eventId,
                                               @RequestBody AssignCommand command) {
        return success(applicationService.assign(eventId, command));
    }

    /**
     * 使用案件保存的真实分片时间重新查询渠道。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间和期望案件版本
     * @return 重新勾兑后的最新案件记录
     */
    @PostMapping("/{eventId}/requery")
    public CommonResult<AbnormalRecord> requery(@PathVariable("eventId") String eventId,
                                                @RequestBody RequeryCommand command) {
        return success(applicationService.requery(eventId, command));
    }

    /**
     * 批量重新查询渠道，单批最多一百笔且单笔失败互相隔离。
     *
     * @param command 待重查案件及其真实分片时间、期望版本
     * @return 批量受理和失败统计
     */
    @PostMapping("/batch-requery")
    public CommonResult<BatchRequeryResult> batchRequery(@RequestBody BatchRequeryCommand command) {
        return success(applicationService.batchRequery(command));
    }

    /**
     * 确认无需修改或忽略案件，不接受浏览器指定交易目标状态。
     *
     * @param eventId 勾兑异常案件号
     * @param command 处置类型、原因、真实分片时间和期望版本
     * @return 处置后的最新案件记录
     */
    @PostMapping("/{eventId}/resolve")
    public CommonResult<AbnormalRecord> resolve(@PathVariable("eventId") String eventId,
                                                @RequestBody ResolveCommand command) {
        return success(applicationService.resolve(eventId, command));
    }
}
