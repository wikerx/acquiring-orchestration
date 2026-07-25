package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.api.AbstractPaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelReversalRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 收单渠道客户端，位于 payment-channel-library 渠道实现层，负责把平台统一渠道能力路由到 MPGS API 客户端；不创建平台交易单、不更新平台交易状态。
 * @status : create
 */
@Component
public class MpgsPaymentChannelClient extends AbstractPaymentChannelClient {

    private final MpgsApiClient mpgsApiClient;

    /**
     * 创建 MPGS 渠道客户端。
     *
     * @param mpgsApiClient MPGS REST API 客户端
     */
    public MpgsPaymentChannelClient(MpgsApiClient mpgsApiClient) {
        this.mpgsApiClient = mpgsApiClient;
    }

    /**
     * 获取 MPGS 渠道编码。
     *
     * @return MPGS 渠道编码
     */
    @Override
    public String channelCode() {
        return PaymentChannelCode.MPGS.getCode();
    }

    /**
     * 获取当前 MPGS 适配器已接入的交易能力。
     *
     * @return MPGS 渠道能力集合
     */
    @Override
    public Set<ChannelCapability> capabilities() {
        return EnumSet.of(
                ChannelCapability.PAYMENT,
                ChannelCapability.AUTHORIZATION,
                ChannelCapability.CAPTURE,
                ChannelCapability.PRE_AUTHORIZATION,
                ChannelCapability.PRE_AUTH_COMPLETION,
                ChannelCapability.INCREMENTAL_AUTHORIZATION,
                ChannelCapability.REFUND,
                ChannelCapability.VOID,
                ChannelCapability.REVERSAL,
                ChannelCapability.QUERY
        );
    }

    /**
     * 提交 MPGS 一步支付交易。
     *
     * @param request 渠道支付请求，需包含卡信息、金额、币种和交易标识
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse payment(ChannelPaymentRequest request) {
        requireCapability(ChannelCapability.PAYMENT);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 授权交易。
     *
     * @param request 渠道授权请求，需包含卡信息、金额、币种和交易标识
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse authorize(ChannelAuthorizeRequest request) {
        requireCapability(ChannelCapability.AUTHORIZATION);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 预授权交易。
     *
     * @param request 渠道预授权请求，当前映射为 MPGS AUTHORIZE
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse preAuthorize(ChannelPreAuthorizeRequest request) {
        requireCapability(ChannelCapability.PRE_AUTHORIZATION);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 增量授权交易。
     *
     * @param request 渠道增量授权请求，当前映射为 MPGS UPDATE_AUTHORIZATION
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse incrementalAuthorize(ChannelIncrementalAuthorizeRequest request) {
        requireCapability(ChannelCapability.INCREMENTAL_AUTHORIZATION);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 请款交易。
     *
     * @param request 渠道请款请求，需包含请款金额、币种和交易标识
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse capture(ChannelCaptureRequest request) {
        requireCapability(ChannelCapability.CAPTURE);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 退款交易。
     *
     * @param request 渠道退款请求，需包含退款金额、币种和交易标识
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse refund(ChannelRefundRequest request) {
        requireCapability(ChannelCapability.REFUND);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 撤销交易。
     *
     * @param request 渠道撤销请求，需通过 sourceTransactionId 或扩展字段 targetTransactionId 指定目标渠道交易
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse voidPayment(ChannelVoidRequest request) {
        requireCapability(ChannelCapability.VOID);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 冲正交易。
     *
     * @param request 渠道冲正请求，当前映射为 MPGS VOID，平台侧仍需在状态机中区分 REVERSAL 语义
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse reversal(ChannelReversalRequest request) {
        requireCapability(ChannelCapability.REVERSAL);
        return mpgsApiClient.execute(request);
    }

    /**
     * 查询 MPGS 交易结果。
     *
     * @param request 渠道查询请求，使用商户订单号和交易动作单号构造 MPGS 查询 URL
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse query(ChannelQueryRequest request) {
        requireCapability(ChannelCapability.QUERY);
        return mpgsApiClient.execute(request);
    }

    /**
     * MPGS RETRIEVE 交易级查询要求同时具备 order.id 与 transaction.id。
     *
     * @param request 渠道查询请求
     * @return true 表示当前查询引用满足 MPGS REST URL 身份要求
     */
    @Override
    public boolean supportsQueryReference(ChannelQueryRequest request) {
        return supports(ChannelCapability.QUERY)
                && request != null
                && StringUtils.hasText(request.getChannelOrderNo())
                && StringUtils.hasText(request.getChannelTransactionId());
    }
}
