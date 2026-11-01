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
 * 将渠道适配层的统一交易状态映射为平台交易状态。
 *
 * <p>本类只解释标准化后的渠道状态，不根据渠道原始文案推断交易结果。缺失或未知状态必须保留为
 * 待确认结果，避免网络超时、回调乱序或新渠道状态被错误写成失败终态。</p>
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
        // 无同步响应只能证明本次调用未取得确定结果，不能据此判定渠道拒绝。
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
        // 回调缺少标准状态时保留渠道证据并等待查询勾兑，不使用原始文案猜测终态。
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
        // AUTHORIZED 只完成授权类动作；支付、请款等动作仍需等待与其语义匹配的渠道终态。
        if (ChannelTradeStatus.AUTHORIZED.getCode().equals(channelTradeStatus)) {
            return isAuthorizationTransaction(transactionType)
                    ? success(rawChannelStatus, channelResponseCode, channelResponseMessage)
                    : waitingCallback(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.CAPTURED.getCode().equals(channelTradeStatus)) {
            return success(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        // REFUNDED 仅能完成退款动作；其他动作收到该状态时交由查询勾兑确认生命周期关系。
        if (ChannelTradeStatus.REFUNDED.getCode().equals(channelTradeStatus)) {
            return PaymentTransactionTypeEnum.REFUND.getCode().equals(transactionType)
                    ? success(rawChannelStatus, channelResponseCode, channelResponseMessage)
                    : processing(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        // SUCCESS/FAILED 是渠道适配层已经确认的通用终态，不再依赖原始 message 判断。
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
        // 对新增、空白或暂未识别的状态保持处理中，禁止把不确定结果降级成失败。
        return processing(rawChannelStatus, channelResponseCode, channelResponseMessage);
    }

    /**
     * 统一授权完成状态只对授权类平台动作构成成功终态。
     */
    private boolean isAuthorizationTransaction(String transactionType) {
        return PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType);
    }

    /**
     * 构造已确认成功终态，保留渠道码用于内部审计。
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
     * 构造已确认渠道失败终态；渠道原始文案不会直接成为商户公开文案。
     */
    private ChannelTransactionStatusResolution failed(String channelStatus,
                                                      String channelResponseCode,
                                                      String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        resolution.setFailReasonCode(PaymentFailureReasonEnum.CHANNEL_DECLINED.getCode());
        resolution.setFailReasonMessage(firstText(channelResponseMessage, "channel declined transaction"));
        return resolution;
    }

    /**
     * 将持卡人交互要求映射为等待 3DS 阶段，不提前结束交易。
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
     * 将渠道处理中状态映射为等待回调，后续由回调或主动查询继续推进。
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
     * 构造没有任何渠道证据时的平台待确认结果。
     */
    private ChannelTransactionStatusResolution processing() {
        return processing(null, null, null);
    }

    /**
     * 构造携带渠道证据的平台待确认结果。
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
     * 保留未解析回调中的非敏感渠道证据，等待查询勾兑。
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
     * 构造状态无法解析的待确认结果，不伪造平台终态。
     */
    private ChannelTransactionStatusResolution unresolved(String channelStatus,
                                                          String channelResponseCode,
                                                          String channelResponseMessage) {
        return base(channelStatus, channelResponseCode, channelResponseMessage);
    }

    /**
     * 初始化内部解析结果；这里只保存证据，不决定公开文案。
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
     * 返回首个非空内部诊断文本；该值不得绕过受众投影直接返回商户或持卡人。
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
