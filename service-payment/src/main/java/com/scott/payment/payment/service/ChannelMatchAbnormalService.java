package com.scott.payment.payment.service;

import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AssignCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalService
 * @date : 2026-08-06 00:00
 * @description : 勾兑异常领域服务，负责幂等建案、查询、领取、重查和不修改交易终态的案件处置。
 * @status : create
 */
public interface ChannelMatchAbnormalService {

    /** @return 查询条件下的案件分页和统计 */
    AbnormalSearchResponse search(AbnormalQuery query);

    /** @return 使用真实分片时间查询的案件详情 */
    AbnormalDetailResponse detail(String eventId, LocalDateTime transactionDateTime);

    /** @return 领取或转派后的案件 */
    AbnormalRecord assign(String eventId, AssignCommand command);

    /** @return 确认无需修改或忽略后的案件 */
    AbnormalRecord resolve(String eventId, ResolveCommand command);

    /** @return 单笔重新勾兑后的案件 */
    AbnormalRecord requery(String eventId, RequeryCommand command);

    /** @return 批量重新勾兑结果 */
    BatchRequeryResult batchRequery(BatchRequeryCommand command);

    /**
     * 达到自动查询阈值后幂等创建或重新打开案件。
     *
     * @param operationDO 交易动作快照
     * @param abnormalType 异常类型稳定编码
     * @param description 脱敏异常说明
     * @param matchResult 勾兑结果摘要
     * @param sourceRecordId 渠道查询请求引用，可为空
     * @param seenTime 本次发现时间
     */
    void recordReviewRequired(TransactionOperationDO operationDO,
                              String abnormalType,
                              String description,
                              String matchResult,
                              String sourceRecordId,
                              LocalDateTime seenTime);

    /**
     * 正常状态机确认一致结果后关闭活动案件，不修改交易状态。
     *
     * @return 关闭案件数
     */
    int autoResolve(String transactionId,
                    LocalDateTime transactionDateTime,
                    String referenceId,
                    LocalDateTime resolvedTime);
}
