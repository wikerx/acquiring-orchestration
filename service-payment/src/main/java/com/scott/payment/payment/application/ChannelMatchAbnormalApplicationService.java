package com.scott.payment.payment.application;

import com.scott.payment.payment.service.ChannelMatchAbnormalService;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AssignCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalApplicationService
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 勾兑异常内部命令应用服务，编排领取、重查和非资金终态处置，不承载管理端查询。
 * @status : create
 */
@Service
public class ChannelMatchAbnormalApplicationService {

    private final ChannelMatchAbnormalService abnormalService;

    /**
     * 创建勾兑异常内部命令应用服务。
     *
     * @param abnormalService 勾兑异常领域服务
     */
    public ChannelMatchAbnormalApplicationService(ChannelMatchAbnormalService abnormalService) {
        this.abnormalService = abnormalService;
    }

    /**
     * 编排案件领取或转派命令。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间、期望版本和可信操作人
     * @return 领取或转派后的最新案件记录
     */
    public AbnormalRecord assign(String eventId, AssignCommand command) {
        return abnormalService.assign(eventId, command);
    }

    /**
     * 编排单笔重新勾兑命令。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间和期望案件版本
     * @return 重新勾兑后的最新案件记录
     */
    public AbnormalRecord requery(String eventId, RequeryCommand command) {
        return abnormalService.requery(eventId, command);
    }

    /**
     * 编排批量重新勾兑命令。
     *
     * @param command 待重查案件及其真实分片时间、期望版本
     * @return 批量受理和失败统计
     */
    public BatchRequeryResult batchRequery(BatchRequeryCommand command) {
        return abnormalService.batchRequery(command);
    }

    /**
     * 编排确认无需修改或忽略案件命令。
     *
     * @param eventId 勾兑异常案件号
     * @param command 处置类型、原因、真实分片时间和期望版本
     * @return 处置后的最新案件记录
     */
    public AbnormalRecord resolve(String eventId, ResolveCommand command) {
        return abnormalService.resolve(eventId, command);
    }
}
