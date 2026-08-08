package com.scott.payment.payment.service;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
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
 * @description : 勾兑异常领域服务，负责幂等建案、领取、重查和不修改交易终态的案件处置；管理端只读查询由 service-admin 承载。
 * @status : create
 */
public interface ChannelMatchAbnormalService {

    /**
     * 领取或转派活动案件，使用期望版本防止并发覆盖。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间、期望版本和可信操作人
     * @return 领取或转派后的最新案件记录
     */
    AbnormalRecord assign(String eventId, AssignCommand command);

    /**
     * 确认无需修改或忽略案件，不允许命令指定交易目标状态。
     *
     * @param eventId 勾兑异常案件号
     * @param command 处置类型、原因、真实分片时间和期望版本
     * @return 处置后的最新案件记录
     */
    AbnormalRecord resolve(String eventId, ResolveCommand command);

    /**
     * 使用案件真实分片时间重新查询渠道并按正常状态机完成勾兑。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间和期望案件版本
     * @return 重新勾兑后的最新案件记录
     */
    AbnormalRecord requery(String eventId, RequeryCommand command);

    /**
     * 批量重新勾兑案件，单笔失败不回滚其他案件。
     *
     * @param command 待重查案件及其真实分片时间、期望版本
     * @return 批量受理和失败统计
     */
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
     * 使用渠道查询返回的结构化金额快照创建或刷新复核案件。
     *
     * @param operationDO 交易动作快照
     * @param abnormalType 异常类型稳定编码
     * @param description 脱敏异常说明
     * @param matchResult 勾兑结果摘要
     * @param sourceRecordId 渠道查询请求引用，可为空
     * @param channelResponse 渠道明确返回的币种和主币种单位金额，可为空
     * @param seenTime 本次发现时间
     */
    void recordReviewRequired(TransactionOperationDO operationDO,
                              String abnormalType,
                              String description,
                              String matchResult,
                              String sourceRecordId,
                              ChannelPaymentResponse channelResponse,
                              LocalDateTime seenTime);

    /**
     * 正常状态机确认一致结果后关闭活动案件，不修改交易状态。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 交易真实分片时间
     * @param referenceId 自动恢复依据引用，可为空
     * @param resolvedTime 案件关闭时间，为空时使用当前时间
     * @return 关闭案件数
     */
    int autoResolve(String transactionId,
                    LocalDateTime transactionDateTime,
                    String referenceId,
                    LocalDateTime resolvedTime);
}
