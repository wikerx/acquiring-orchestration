package com.scott.payment.channel.payment.api;

import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelReversalRequest;
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelUnsupportedOperationException;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道客户端 SPI，位于 payment-channel-api API 层，用于统一授权、支付、请款、退款、撤销、冲正和查询等渠道能力。
 * @status : create
 */
public interface PaymentChannelClient {

    /**
     * 获取渠道编码。
     *
     * @return 渠道编码
     */
    String channelCode();

    /**
     * 获取渠道支持的交易能力。
     *
     * @return 渠道能力集合
     */
    Set<ChannelCapability> capabilities();

    /**
     * 判断渠道是否支持指定能力。
     *
     * @param capability 渠道能力
     * @return true 表示支持
     */
    default boolean supports(ChannelCapability capability) {
        return capabilities() != null && capabilities().contains(capability);
    }

    /**
     * 执行请求指定的单个 3DS 后端阶段。
     *
     * <p>具体渠道负责单阶段协议调用和原始状态解释；浏览器 Method/Challenge 由业务编排层执行，
     * 禁止渠道实现跨浏览器交互连续调用多个阶段。</p>
     *
     * @param request 渠道统一 3DS 认证请求
     * @return 渠道统一 3DS 认证响应
     */
    default ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
        throw unsupported(ChannelCapability.THREE_DS_AUTHENTICATION);
    }

    /**
     * 提交一步支付交易。
     *
     * @param request 渠道支付请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse payment(ChannelPaymentRequest request) {
        throw unsupported(ChannelCapability.PAYMENT);
    }

    /**
     * 提交授权交易。
     *
     * @param request 渠道授权请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse authorize(ChannelAuthorizeRequest request) {
        throw unsupported(ChannelCapability.AUTHORIZATION);
    }

    /**
     * 提交预授权交易。
     *
     * @param request 渠道预授权请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse preAuthorize(ChannelPreAuthorizeRequest request) {
        throw unsupported(ChannelCapability.PRE_AUTHORIZATION);
    }

    /**
     * 提交增量授权交易。
     *
     * @param request 渠道增量授权请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse incrementalAuthorize(ChannelIncrementalAuthorizeRequest request) {
        throw unsupported(ChannelCapability.INCREMENTAL_AUTHORIZATION);
    }

    /**
     * 提交请款交易。
     *
     * @param request 渠道请款请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse capture(ChannelCaptureRequest request) {
        throw unsupported(ChannelCapability.CAPTURE);
    }

    /**
     * 提交退款交易。
     *
     * @param request 渠道退款请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse refund(ChannelRefundRequest request) {
        throw unsupported(ChannelCapability.REFUND);
    }

    /**
     * 提交撤销交易。
     *
     * @param request 渠道撤销请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse voidPayment(ChannelVoidRequest request) {
        throw unsupported(ChannelCapability.VOID);
    }

    /**
     * 提交冲正交易。
     *
     * @param request 渠道冲正请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse reversal(ChannelReversalRequest request) {
        throw unsupported(ChannelCapability.REVERSAL);
    }

    /**
     * 查询渠道交易。
     *
     * @param request 渠道查询请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse query(ChannelQueryRequest request) {
        throw unsupported(ChannelCapability.QUERY);
    }

    /**
     * 判断当前渠道是否支持使用请求中的持久化身份发起查询。
     * <p>
     * 默认规则允许渠道实现使用 channelTransactionId、channelOrderNo 或 requestId 执行查询；具体渠道若有更严格的 REST
     * 身份要求，应在渠道实现内覆盖该方法，避免通用业务服务硬编码渠道差异。
     *
     * @param request 渠道查询请求
     * @return true 表示当前查询引用可被该渠道识别
     */
    default boolean supportsQueryReference(ChannelQueryRequest request) {
        return supports(ChannelCapability.QUERY)
                && request != null
                && (hasText(request.getChannelTransactionId())
                || hasText(request.getChannelOrderNo())
                || hasText(request.getRequestId())
                || hasText(request.getExtension().get("requestId")));
    }

    /**
     * 构造渠道不支持能力异常。
     *
     * @param capability 渠道能力
     * @return 渠道不支持能力异常
     */
    default ChannelUnsupportedOperationException unsupported(ChannelCapability capability) {
        return new ChannelUnsupportedOperationException(channelCode(), capability.getCode());
    }

    /**
     * 判断 has text 条件是否成立，用于控制 Payment Channel Client 的后续分支。
     * <p>
     * 纯判断操作，不修改业务状态。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 条件满足时返回 true，否则返回 false
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
