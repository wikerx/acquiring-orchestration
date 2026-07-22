package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionChannelMatchService
 * @date : 2026-07-19 22:30
 * @email : scott_x@163.com
 * @description : 默认渠道交易查询勾兑服务，位于 service-payment 服务实现层，按原动作单保存的渠道和 MID 快照发起 QUERY；仅在渠道查询确认 SUCCESS/FAILED 时推进终态，网络和解析异常只保留待下次勾兑。
 * @status : create
 */
@Service
public class DefaultTransactionChannelMatchService implements TransactionChannelMatchService {

    private static final int DEFAULT_LIMIT = 100;

    private static final int MAX_LIMIT = 500;

    private static final String CHANNEL_MATCH_PENDING = "PENDING";

    private static final String MATCH_EVENT_PREFIX = "CCM";

    private final TransactionRecordService transactionRecordService;

    private final PaymentChannelInvokeService paymentChannelInvokeService;

    private final PaymentChannelRouteService paymentChannelRouteService;

    private final ChannelTransactionStatusResolver channelStatusResolver;

    /**
     * 创建默认渠道交易查询勾兑服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param paymentChannelInvokeService 渠道调用服务
     * @param paymentChannelRouteService 渠道路由服务
     * @param channelStatusResolver 渠道状态解析服务
     */
    public DefaultTransactionChannelMatchService(TransactionRecordService transactionRecordService,
                                                PaymentChannelInvokeService paymentChannelInvokeService,
                                                PaymentChannelRouteService paymentChannelRouteService,
                                                ChannelTransactionStatusResolver channelStatusResolver) {
        this.transactionRecordService = transactionRecordService;
        this.paymentChannelInvokeService = paymentChannelInvokeService;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.channelStatusResolver = channelStatusResolver;
    }

    /**
     * 处理待渠道查询确认的交易动作。
     *
     * @param commandDTO 查询勾兑命令
     * @return 本次处理结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionChannelMatchResultDTO matchDue(TransactionChannelMatchCommandDTO commandDTO) {
        LocalDateTime transactionDateTime = commandDTO == null ? null : commandDTO.getTransactionDateTime();
        LocalDateTime now = LocalDateTime.now();
        int limit = normalizeLimit(commandDTO == null ? null : commandDTO.getLimit());
        TransactionChannelMatchResultDTO resultDTO = new TransactionChannelMatchResultDTO();
        for (TransactionOperationDO operationDO : transactionRecordService.listPendingChannelMatch(
                transactionDateTime,
                commandDTO == null ? null : commandDTO.getChannelCode(),
                now,
                limit)) {
            resultDTO.setScannedCount(resultDTO.getScannedCount() + 1);
            processOne(operationDO, now, resultDTO);
        }
        return resultDTO;
    }

    /**
     * 查询并处理单笔待勾兑动作。
     * <p>
     * 只有渠道查询结果被解析为 SUCCESS/FAILED 终态时才推进交易；未识别、处理中、网络异常和解析异常都保留为待勾兑，
     * 避免把渠道可能已经成功的资金动作误标失败。
     *
     * @param operationDO 待查询动作单
     * @param now 本次勾兑时间
     * @param resultDTO 本次任务统计结果
     */
    private void processOne(TransactionOperationDO operationDO,
                            LocalDateTime now,
                            TransactionChannelMatchResultDTO resultDTO) {
        try {
            PaymentChannelInvokeResultDTO invokeResultDTO = paymentChannelInvokeService.invoke(
                    toQueryCommand(operationDO),
                    restoreRouteResult(operationDO),
                    operationDO.getOperationId(),
                    operationDO.getTransactionId(),
                    operationDO.getChannelOrderNo());
            ChannelPaymentResponse response = invokeResultDTO == null ? null : invokeResultDTO.getChannelResponse();
            ChannelTransactionStatusResolution resolution = channelStatusResolver.resolveSync(
                    operationDO.getChannelCode(),
                    operationDO.getTransactionType(),
                    response);
            if (!resolution.resolved()) {
                markPending(operationDO, now, invokeResultDTO, "channel query status can not be mapped yet");
                resultDTO.setPendingCount(resultDTO.getPendingCount() + 1);
                return;
            }
            if ("SUCCESS".equals(resolution.getTargetStatus()) || "FAILED".equals(resolution.getTargetStatus())) {
                complete(operationDO, resolution, now);
                resultDTO.setMatchedCount(resultDTO.getMatchedCount() + 1);
                return;
            }
            markPending(operationDO, now, invokeResultDTO, resolution.getTargetStatus());
            resultDTO.setPendingCount(resultDTO.getPendingCount() + 1);
        } catch (RuntimeException exception) {
            // 查询异常无法证明渠道失败，资金动作必须保持非终态，等待下一次查询或回调确认。
            markPending(operationDO, now, null, "QUERY_EXCEPTION", exception.getMessage());
            resultDTO.setFailedCount(resultDTO.getFailedCount() + 1);
        }
    }

    /**
     * 使用渠道查询确认结果推进交易终态。
     * <p>
     * 这里复用回调终态推进服务，以保持 CAS、状态历史、金额汇总和商户通知激活口径一致。
     *
     * @param operationDO 待推进动作单
     * @param resolution 渠道查询解析出的终态结果
     * @param now 当前处理时间
     */
    private void complete(TransactionOperationDO operationDO,
                          ChannelTransactionStatusResolution resolution,
                          LocalDateTime now) {
        TransactionOrderDO orderDO = transactionRecordService.findOrder(operationDO.getTransactionDateTime(), operationDO.getOperationId());
        transactionRecordService.completeByChannelCallback(
                operationDO,
                orderDO,
                PaymentOrderNoGenerator.nextOrderNo(MATCH_EVENT_PREFIX, operationDO.getTransactionDateTime()),
                resolution.getTargetStatus(),
                resolution.getFailReasonCode(),
                resolution.getFailReasonMessage(),
                resolution.getChannelStatus(),
                resolution.getChannelResponseCode(),
                resolution.getChannelResponseMessage());
    }

    /**
     * 标记动作单继续等待渠道查询或回调确认。
     *
     * @param operationDO 待更新动作单
     * @param now 本次勾兑时间
     * @param invokeResultDTO 本次渠道查询调用上下文，可为空
     * @param reason 勾兑结果摘要
     */
    private void markPending(TransactionOperationDO operationDO,
                             LocalDateTime now,
                             PaymentChannelInvokeResultDTO invokeResultDTO,
                             String reason) {
        markPending(operationDO, now, invokeResultDTO, reason, null);
    }

    /**
     * 标记动作单继续等待并记录异常摘要。
     * <p>
     * QUERY 异常、响应无法解析或非终态结果都不能证明资金失败，因此只更新勾兑摘要、最近请求号和下次查询时间。
     *
     * @param operationDO 待更新动作单
     * @param now 本次勾兑时间
     * @param invokeResultDTO 本次渠道查询调用上下文，可为空
     * @param reason 勾兑结果摘要
     * @param failReason 技术异常摘要，可为空
     */
    private void markPending(TransactionOperationDO operationDO,
                             LocalDateTime now,
                             PaymentChannelInvokeResultDTO invokeResultDTO,
                             String reason,
                             String failReason) {
        transactionRecordService.updateChannelMatch(operationDO,
                CHANNEL_MATCH_PENDING,
                reason,
                invokeResultDTO == null ? null : invokeResultDTO.getRequestId(),
                now,
                nextMatchTime(operationDO, now),
                failReason);
    }

    /**
     * 构造渠道 QUERY 命令。
     * <p>
     * 查询勾兑复用 payment 的渠道调用服务，但交易类型固定为 QUERY，金额和币种只作为渠道查询上下文，
     * 不会创建新的支付动作或重新计算金额。
     *
     * @param operationDO 待查询动作单
     * @return 查询命令
     */
    private PaymentCreateCommandDTO toQueryCommand(TransactionOperationDO operationDO) {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId(operationDO.getMerchantId());
        commandDTO.setMerchantOrderNo(operationDO.getMerchantOrderNo());
        commandDTO.setMerchantOrderId(operationDO.getMerchantOrderId());
        commandDTO.setTransactionType("QUERY");
        commandDTO.setTransactionDateTime(operationDO.getTransactionDateTime());
        commandDTO.setAmount(operationDO.getTransactionAmount());
        commandDTO.setCurrency(operationDO.getTransactionCurrency());
        commandDTO.setTransactionAmount(operationDO.getTransactionAmount());
        commandDTO.setTransactionCurrency(operationDO.getTransactionCurrency());
        return commandDTO;
    }

    /**
     * 恢复原交易动作的渠道路由快照。
     * <p>
     * 勾兑查询必须使用原动作单上的 channel_id、channel_mid_config_id、channel_order_no 和 channel_transaction_id；
     * 不能重新路由，否则渠道配置变更后可能查错 MID，导致误判交易状态。
     *
     * @param operationDO 待查询动作单
     * @return 原渠道调用参数
     */
    private PaymentRouteResultDTO restoreRouteResult(TransactionOperationDO operationDO) {
        return paymentChannelRouteService.restore(
                operationDO.getChannelCode(),
                operationDO.getChannelId(),
                operationDO.getChannelMidConfigId(),
                operationDO.getChannelTerminalId());
    }

    /**
     * 计算下一次查询时间。
     * <p>
     * 使用简易递增退避，最大间隔 60 分钟，避免渠道长时间处理中时频繁查询。
     *
     * @param operationDO 待查询动作单
     * @param now 当前处理时间
     * @return 下一次查询时间
     */
    private LocalDateTime nextMatchTime(TransactionOperationDO operationDO, LocalDateTime now) {
        int matchCount = operationDO.getChannelMatchCount() == null ? 0 : operationDO.getChannelMatchCount();
        long minutes = Math.min(60L, Math.max(1L, matchCount + 1L) * 5L);
        return now.plusMinutes(minutes);
    }

    /**
     * 规范化单次扫描数量。
     *
     * @param limit 外部传入扫描数量
     * @return 限制后的扫描数量
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
