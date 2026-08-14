package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultChannelTransactionStatusResolver
 * @date : 2026-07-19 22:00
 * @email : scott_x@163.com
 * @description : 默认渠道状态解析器，位于 service-payment 服务实现层，只根据渠道统一动作状态和平台交易类型决定平台状态。
 * @status : create
 */
@Service
public class DefaultChannelTransactionStatusResolver implements ChannelTransactionStatusResolver {

    /**
     * 解析渠道同步响应。
     *
     * @param channelCode 渠道编码
     * @param transactionType 平台交易类型
     * @param response 渠道同步响应
     * @return 平台状态解析结果
     */
    @Override
    public ChannelTransactionStatusResolution resolveSync(String channelCode,
                                                          String transactionType,
                                                          ChannelPaymentResponse response) {
        if (response == null) {
            return processing();
        }
        return resolveGeneric(transactionType,
                response.getChannelTradeStatus(),
                response.getRawChannelStatus(),
                response.getChannelResponseCode(),
                response.getChannelResponseMessage());
    }

    /**
     * 解析渠道回调或查询结果。
     *
     * @param channelCode 渠道编码
     * @param transactionType 平台交易类型
     * @param callbackResult 渠道回调或查询结果
     * @return 平台状态解析结果
     */
    @Override
    public ChannelTransactionStatusResolution resolveCallback(String channelCode,
                                                              String transactionType,
                                                              ChannelCallbackResult callbackResult) {
        if (callbackResult == null || !StringUtils.hasText(callbackResult.getChannelTradeStatus())) {
            return unresolved(callbackResult);
        }
        return resolveGeneric(transactionType,
                callbackResult.getChannelTradeStatus(),
                callbackResult.getRawChannelStatus(),
                callbackResult.getChannelResponseCode(),
                callbackResult.getChannelResponseMessage());
    }

    /**
     * 按通用渠道状态映射平台交易状态。
     * <p>
     * AUTHORIZED/CAPTURED/REFUNDED 结合交易类型解释；SUCCESS/FAILED 进入终态，
     * NEED_REDIRECT/PENDING 保持待处理，其他状态按处理中处理。
     *
     * @param channelTradeStatus 渠道统一状态
     * @param rawChannelStatus 渠道原始状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 平台状态解析结果
     */
    private ChannelTransactionStatusResolution resolveGeneric(String transactionType,
                                                              String channelTradeStatus,
                                                              String rawChannelStatus,
                                                              String channelResponseCode,
                                                              String channelResponseMessage) {
        if (ChannelTradeStatus.AUTHORIZED.getCode().equals(channelTradeStatus)) {
            return isAuthorizationTransaction(transactionType)
                    ? success(rawChannelStatus, channelResponseCode, channelResponseMessage)
                    : waitingCallback(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.CAPTURED.getCode().equals(channelTradeStatus)) {
            return success(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.REFUNDED.getCode().equals(channelTradeStatus)) {
            return PaymentTransactionTypeEnum.REFUND.getCode().equals(transactionType)
                    ? success(rawChannelStatus, channelResponseCode, channelResponseMessage)
                    : processing(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.SUCCESS.getCode().equals(channelTradeStatus)) {
            return success(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.FAILED.getCode().equals(channelTradeStatus)) {
            return failed(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.NEED_REDIRECT.getCode().equals(channelTradeStatus)) {
            return needRedirect(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.PENDING.getCode().equals(channelTradeStatus)) {
            return waitingCallback(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        return processing(rawChannelStatus, channelResponseCode, channelResponseMessage);
    }

    /** 统一授权完成状态只对授权类平台动作构成成功终态。 */
    private boolean isAuthorizationTransaction(String transactionType) {
        return PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType);
    }

    /**
     * 构造平台成功终态解析结果。
     * <p>
     * 前置条件：调用方已确认渠道状态可代表当前交易类型的成功语义；该方法只组装解析结果，不写数据库、不发布 MQ。
     * </p>
     *
     * @param channelStatus 渠道原始状态或统一状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 平台成功终态解析结果
     */
    private ChannelTransactionStatusResolution success(String channelStatus,
                                                       String channelResponseCode,
                                                       String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        return resolution;
    }

    /**
     * 构造平台失败终态解析结果。
     * <p>
     * 前置条件：调用方已确认渠道状态或响应码表示拒绝、失败或不可恢复异常；该方法只设置失败原因，不覆盖终态交易。
     * </p>
     *
     * @param channelStatus 渠道原始状态或统一状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 平台失败终态解析结果
     */
    private ChannelTransactionStatusResolution failed(String channelStatus,
                                                      String channelResponseCode,
                                                      String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        resolution.setFailReasonCode(PaymentFailureReasonEnum.CHANNEL_REQUEST_FAILED.getCode());
        resolution.setFailReasonMessage(firstText(channelResponseMessage, "channel declined transaction"));
        return resolution;
    }

    /**
     * 构造等待 3DS 跳转的解析结果。
     * <p>
     * 前置条件：渠道明确返回 NEED_REDIRECT；平台交易保持 PENDING，后续由回跳、回调或查询继续推进。
     * </p>
     *
     * @param channelStatus 渠道原始状态或统一状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 等待 3DS 跳转的状态解析结果
     */
    private ChannelTransactionStatusResolution needRedirect(String channelStatus,
                                                            String channelResponseCode,
                                                            String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.PENDING.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.WAITING_3DS.getCode());
        resolution.setPendingReasonCode(PaymentPendingReasonEnum.NEED_REDIRECT.getCode());
        return resolution;
    }

    /**
     * 构造等待渠道回调或查询确认的解析结果。
     * <p>
     * 前置条件：渠道同步响应只表示受理、授权层完成或处理中，尚不足以确认平台资金终态。
     * </p>
     *
     * @param channelStatus 渠道原始状态或统一状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 等待渠道回调的状态解析结果
     */
    private ChannelTransactionStatusResolution waitingCallback(String channelStatus,
                                                               String channelResponseCode,
                                                               String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.PENDING.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.WAITING_CALLBACK.getCode());
        resolution.setPendingReasonCode(PaymentPendingReasonEnum.WAITING_CHANNEL_CALLBACK.getCode());
        return resolution;
    }

    /**
     * 构造默认处理中解析结果。
     * <p>
     * 前置条件：渠道未返回可决策状态或响应为空；平台保持 CHANNEL_PROCESSING，等待后续查询或回调。
     * </p>
     *
     * @return 默认处理中状态解析结果
     */
    private ChannelTransactionStatusResolution processing() {
        return processing(null, null, null);
    }

    /**
     * 构造带渠道摘要的处理中解析结果。
     * <p>
     * 前置条件：渠道状态无法确认成功或失败；该方法保存原始渠道摘要，便于后续查询、回调和日志排查。
     * </p>
     *
     * @param channelStatus 渠道原始状态或统一状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 处理中状态解析结果
     */
    private ChannelTransactionStatusResolution processing(String channelStatus,
                                                          String channelResponseCode,
                                                          String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        return resolution;
    }

    /**
     * 构造未解析回调状态结果。
     * <p>
     * 前置条件：回调结果为空或缺少渠道统一状态；调用方保留当前平台状态，不应推进成功或失败终态。
     * </p>
     *
     * @param callbackResult 渠道回调解析结果
     * @return 未解析状态结果
     */
    private ChannelTransactionStatusResolution unresolved(ChannelCallbackResult callbackResult) {
        if (callbackResult == null) {
            return new ChannelTransactionStatusResolution();
        }
        return unresolved(callbackResult.getRawChannelStatus(),
                callbackResult.getChannelResponseCode(),
                callbackResult.getChannelResponseMessage());
    }

    /**
     * 构造带渠道摘要的未解析状态结果。
     * <p>
     * 前置条件：渠道返回了平台当前无法识别的状态；该方法只保留摘要字段，不设置目标状态和处理阶段。
     * </p>
     *
     * @param channelStatus 渠道原始状态或统一状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 未解析状态结果
     */
    private ChannelTransactionStatusResolution unresolved(String channelStatus,
                                                          String channelResponseCode,
                                                          String channelResponseMessage) {
        return base(channelStatus, channelResponseCode, channelResponseMessage);
    }

    /**
     * 构造状态解析结果基础对象。
     * <p>
     * 前置条件：调用方已完成渠道状态判断；该方法只复制渠道摘要字段，不设置平台目标状态。
     * </p>
     *
     * @param channelStatus 渠道原始状态或统一状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 带渠道摘要的状态解析结果
     */
    private ChannelTransactionStatusResolution base(String channelStatus,
                                                    String channelResponseCode,
                                                    String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = new ChannelTransactionStatusResolution();
        resolution.setChannelStatus(channelStatus);
        resolution.setChannelResponseCode(channelResponseCode);
        resolution.setChannelResponseMessage(channelResponseMessage);
        return resolution;
    }

    /**
     * 返回首个非空渠道摘要文本。
     *
     * @param values 候选渠道状态、响应码或响应描述
     * @return 首个非空文本；全部为空时返回 null
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
