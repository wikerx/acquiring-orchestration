package com.scott.payment.payment.application;

import com.scott.payment.payment.service.ChannelMatchAbnormalService;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AssignCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalApplicationService
 * @date : 2026-08-06 00:00
 * @description : 勾兑异常内部应用服务，编排查询、领取、重查和非资金终态处置能力。
 * @status : create
 */
@Service
public class ChannelMatchAbnormalApplicationService {

    private final ChannelMatchAbnormalService abnormalService;

    /** @param abnormalService 勾兑异常领域服务 */
    public ChannelMatchAbnormalApplicationService(ChannelMatchAbnormalService abnormalService) {
        this.abnormalService = abnormalService;
    }

    /** @return 案件分页和统计 */
    public AbnormalSearchResponse search(AbnormalQuery query) {
        return abnormalService.search(query);
    }

    /** @return 案件聚合详情 */
    public AbnormalDetailResponse detail(String eventId, LocalDateTime transactionDateTime) {
        return abnormalService.detail(eventId, transactionDateTime);
    }

    /** @return 领取或转派后的案件 */
    public AbnormalRecord assign(String eventId, AssignCommand command) {
        return abnormalService.assign(eventId, command);
    }

    /** @return 单笔重查后的案件 */
    public AbnormalRecord requery(String eventId, RequeryCommand command) {
        return abnormalService.requery(eventId, command);
    }

    /** @return 批量重查结果 */
    public BatchRequeryResult batchRequery(BatchRequeryCommand command) {
        return abnormalService.batchRequery(command);
    }

    /** @return 确认无需修改或忽略后的案件 */
    public AbnormalRecord resolve(String eventId, ResolveCommand command) {
        return abnormalService.resolve(eventId, command);
    }
}
