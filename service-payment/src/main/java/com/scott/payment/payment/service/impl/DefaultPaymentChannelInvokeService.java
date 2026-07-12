package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.executor.PaymentChannelExecutor;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentChannelInvokeService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道调用默认实现，位于 service-payment 服务实现层，负责把支付核心上下文转换为渠道请求并通过 PaymentChannelExecutor 调用渠道 SPI。
 * @status : create
 */
@Service
public class DefaultPaymentChannelInvokeService implements PaymentChannelInvokeService {

    private final PaymentChannelExecutor paymentChannelExecutor;

    /**
     * 创建渠道调用服务。
     *
     * @param paymentChannelExecutor 渠道执行器
     */
    public DefaultPaymentChannelInvokeService(PaymentChannelExecutor paymentChannelExecutor) {
        this.paymentChannelExecutor = paymentChannelExecutor;
    }

    @Override
    public ChannelPaymentResponse invoke(PaymentCreateCommandDTO commandDTO,
                                         PaymentRouteResultDTO routeResult,
                                         String transactionOrderNo,
                                         String transactionNo) {
        return paymentChannelExecutor.execute(toChannelRequest(commandDTO, routeResult, transactionOrderNo, transactionNo));
    }

    /**
     * 构造渠道统一请求。
     *
     * @param commandDTO  创建交易命令
     * @param routeResult 路由结果
     * @return 渠道统一请求
     */
    private ChannelPaymentRequest toChannelRequest(PaymentCreateCommandDTO commandDTO,
                                                   PaymentRouteResultDTO routeResult,
                                                   String transactionOrderNo,
                                                   String transactionNo) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(routeResult.getChannelCode());
        request.setTransactionNo(transactionNo);
        request.setTransactionOrderNo(transactionOrderNo);
        request.setMerchantId(commandDTO.getMerchantId());
        request.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        request.setTransactionType(commandDTO.getTransactionType());
        request.setPaymentMethod(commandDTO.getPaymentMethod());
        request.setAmount(commandDTO.getAmount());
        request.setCurrency(commandDTO.getCurrency());
        request.setTransactionDateTime(commandDTO.getTransactionDateTime());
        request.setOriginalTransactionNo(commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId());
        if (commandDTO.getCardInfo() != null) {
            request.setCardNo(commandDTO.getCardInfo().getCardNo());
            request.setExpirationMonth(commandDTO.getCardInfo().getExpirationMonth());
            request.setExpirationYear(commandDTO.getCardInfo().getExpirationYear());
            request.setSecurityCode(commandDTO.getCardInfo().getSecurityCode());
        }
        if (commandDTO.getTransactionInfo() != null) {
            request.setCardBrand(commandDTO.getTransactionInfo().getCardBrand());
        }
        request.setBillingInfo(toBillingInfo(commandDTO.getBillingCardHolderInfo()));
        request.setThreeDsInfo(toThreeDsInfo(commandDTO.getThreeDsInfo()));
        request.getExtension().put("callbackUrl", emptyIfNull(commandDTO.getCallbackUrl()));
        request.getExtension().put("sourceUrl", emptyIfNull(commandDTO.getSourceUrl()));
        request.getExtension().put("payerIp", emptyIfNull(commandDTO.getPayerIp()));
        request.getExtension().put("userAgent", emptyIfNull(commandDTO.getUserAgent()));
        request.getExtension().put("midNo", emptyIfNull(routeResult.getMidNo()));
        request.getExtension().put("midConfigId", routeResult.getMidConfigId() == null ? "" : String.valueOf(routeResult.getMidConfigId()));
        request.getExtension().put("requestUrl", emptyIfNull(routeResult.getRequestUrl()));
        request.getExtension().put("connectTimeoutSeconds", routeResult.getConnectTimeoutSeconds() == null ? "" : String.valueOf(routeResult.getConnectTimeoutSeconds()));
        request.getExtension().put("readTimeoutSeconds", routeResult.getReadTimeoutSeconds() == null ? "" : String.valueOf(routeResult.getReadTimeoutSeconds()));
        for (Map.Entry<String, String> entry : routeResult.getMetadataValues().entrySet()) {
            request.getExtension().put("mid." + entry.getKey(), emptyIfNull(entry.getValue()));
        }
        return request;
    }

    private ChannelPaymentRequest.BillingInfo toBillingInfo(PaymentCreateCommandDTO.BillingCardHolderInfoDTO source) {
        if (source == null) {
            return null;
        }
        ChannelPaymentRequest.BillingInfo target = new ChannelPaymentRequest.BillingInfo();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setPhone(source.getPhone());
        target.setEmail(source.getEmail());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    private ChannelPaymentRequest.ThreeDsInfo toThreeDsInfo(PaymentCreateCommandDTO.ThreeDsInfoDTO source) {
        if (source == null) {
            return null;
        }
        ChannelPaymentRequest.ThreeDsInfo target = new ChannelPaymentRequest.ThreeDsInfo();
        target.setEci(source.getEci());
        target.setCavv(source.getCavv());
        target.setDsTransactionId(source.getDsTransactionId());
        target.setThreeDsVersion(source.getThreeDsVersion());
        return target;
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
