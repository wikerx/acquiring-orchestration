package com.scott.payment.openapi.converter;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.dto.body.PaymentCreateRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestConverter
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 请求对象转换器，位于 service-openapi 转换层，只做字段映射，不承担币种金额精度换算。
 * @status : create
 */
@Mapper(componentModel = "spring")
public interface OpenApiRequestConverter {

    /**
     * 将普通收单创建 DTO 转换为创建响应。
     *
     * @param requestDTO 普通收单创建 DTO
     * @return 创建响应
     */
    @Mapping(target = "orderInfo", ignore = true)
    @Mapping(target = "transactionInfo", ignore = true)
    @Mapping(target = "merchantInfo", ignore = true)
    @Mapping(target = "billingInfo", ignore = true)
    @Mapping(target = "status", ignore = true)
    PaymentCreateVO toPaymentCreateVO(PaymentCreateRequestDTO requestDTO);

    /**
     * 将 service-payment 创建交易响应转换为商户 OpenAPI 响应。
     *
     * @param responseDTO 支付内部创建响应
     * @return 商户 OpenAPI 创建响应
     */
    default PaymentCreateVO toPaymentCreateVO(PaymentCreateClientResponseDTO responseDTO) {
        PaymentCreateVO vo = new PaymentCreateVO();
        if (responseDTO == null) {
            return vo;
        }
        vo.setMerchantInfo(toMerchantInfoVO(responseDTO));

        PaymentCreateVO.OrderInfoVO orderInfoVO = new PaymentCreateVO.OrderInfoVO();
        orderInfoVO.setOrderNo(responseDTO.getMerchantOrderNo());
        orderInfoVO.setOrderId(responseDTO.getMerchantOrderId());
        orderInfoVO.setAmount(responseDTO.getOrderAmount());
        orderInfoVO.setCurrency(responseDTO.getOrderCurrency());
        orderInfoVO.setTotalAuthorizedAmount(responseDTO.getTotalAuthorizedAmount());
        orderInfoVO.setTotalCapturedAmount(responseDTO.getTotalCapturedAmount());
        orderInfoVO.setTotalRefundAmount(responseDTO.getTotalRefundAmount());
        orderInfoVO.setTotalVoidAmount(responseDTO.getTotalVoidAmount());
        orderInfoVO.setTotalChargebackAmount(responseDTO.getTotalChargebackAmount());
        vo.setOrderInfo(orderInfoVO);

        PaymentCreateVO.TransactionInfoVO transactionInfoVO = new PaymentCreateVO.TransactionInfoVO();
        transactionInfoVO.setCode(responseDTO.getMerchantResponseCode());
        transactionInfoVO.setMessage(responseDTO.getMerchantResponseMessage());
        transactionInfoVO.setTransactionId(responseDTO.getTransactionId());
        transactionInfoVO.setSourceTransactionId(responseDTO.getSourceTransactionId());
        transactionInfoVO.setTransactionType(responseDTO.getTransactionType());
        transactionInfoVO.setTransactionStatus(responseDTO.getStatus());
        transactionInfoVO.setProcessStage(responseDTO.getProcessStage());
        transactionInfoVO.setTransactionDateTime(toOffsetDateTime(responseDTO.getTransactionDateTime(), responseDTO.getTransactionTimeZone()));
        transactionInfoVO.setPaymentMethod(responseDTO.getPaymentMethod());
        transactionInfoVO.setCardBrand(responseDTO.getPaymentBrand());
        transactionInfoVO.setCardBin(responseDTO.getCardBin());
        transactionInfoVO.setAuthCode(responseDTO.getAuthCode());
        transactionInfoVO.setArn(responseDTO.getAcquirerReferenceNo());
        transactionInfoVO.setDescription(responseDTO.getDescription());
        transactionInfoVO.setCallbackUrl(responseDTO.getCallbackUrl());
        transactionInfoVO.setFailReasonCode(responseDTO.getFailReasonCode());
        transactionInfoVO.setFailReasonMessage(responseDTO.getFailReasonMessage());
        transactionInfoVO.setPendingReasonCode(responseDTO.getPendingReasonCode());
        vo.setTransactionInfo(transactionInfoVO);

        vo.setCurrency(responseDTO.getCurrency());
        vo.setStatus(responseDTO.getStatus());
        vo.setBillingInfo(toBillingInfoVO(responseDTO));
        return vo;
    }

    /**
     * 将商户收单支付授权请求 DTO 转换为创建响应。
     *
     * @param requestDTO 商户收单支付授权请求 DTO
     * @return 创建响应
     */
    default PaymentCreateVO toPaymentCreateVO(ApiMerchantPaymentRequestDTO requestDTO) {
        PaymentCreateVO vo = new PaymentCreateVO();
        if (requestDTO == null || requestDTO.getOrderInfo() == null) {
            return vo;
        }
        if (requestDTO.getMerchantInfo() != null) {
            PaymentCreateVO.MerchantInfoVO merchantInfoVO = new PaymentCreateVO.MerchantInfoVO();
            merchantInfoVO.setMerchantId(requestDTO.getMerchantInfo().getMerchantId());
            merchantInfoVO.setSubMerchantInfo(toSubMerchantInfoVO(requestDTO.getMerchantInfo().getSubMerchantInfo()));
            vo.setMerchantInfo(merchantInfoVO);
        }
        ApiMerchantPaymentRequestDTO.OrderInfoDTO orderInfo = requestDTO.getOrderInfo();
        PaymentCreateVO.OrderInfoVO orderInfoVO = new PaymentCreateVO.OrderInfoVO();
        orderInfoVO.setOrderNo(orderInfo.getOrderNo());
        orderInfoVO.setOrderId(orderInfo.getOrderId());
        orderInfoVO.setAmount(orderInfo.getAmount());
        orderInfoVO.setCurrency(orderInfo.getCurrency());
        vo.setOrderInfo(orderInfoVO);
        vo.setCurrency(orderInfo.getCurrency());
        return vo;
    }

    private PaymentCreateVO.MerchantInfoVO toMerchantInfoVO(PaymentCreateClientResponseDTO responseDTO) {
        if (!StringUtils.hasText(responseDTO.getMerchantId()) && responseDTO.getSubMerchantInfo() == null) {
            return null;
        }
        PaymentCreateVO.MerchantInfoVO merchantInfoVO = new PaymentCreateVO.MerchantInfoVO();
        merchantInfoVO.setMerchantId(responseDTO.getMerchantId());
        merchantInfoVO.setSubMerchantInfo(toSubMerchantInfoVO(responseDTO.getSubMerchantInfo()));
        return merchantInfoVO;
    }

    private PaymentCreateVO.SubMerchantInfoVO toSubMerchantInfoVO(PaymentCreateClientResponseDTO.SubMerchantInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateVO.SubMerchantInfoVO target = new PaymentCreateVO.SubMerchantInfoVO();
        target.setSubId(source.getSubId());
        target.setSubName(source.getSubName());
        target.setSubCompanyName(source.getSubCompanyName());
        target.setSubCountryCode(source.getSubCountryCode());
        target.setSubState(source.getSubState());
        target.setSubCity(source.getSubCity());
        target.setSubStreet(source.getSubStreet());
        target.setMerchantCategory(source.getMerchantCategory());
        target.setIntesCode(source.getIntesCode());
        target.setChargeType(source.getChargeType());
        return target;
    }

    private PaymentCreateVO.SubMerchantInfoVO toSubMerchantInfoVO(ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateVO.SubMerchantInfoVO target = new PaymentCreateVO.SubMerchantInfoVO();
        target.setSubId(source.getSubId());
        target.setSubName(source.getSubName());
        target.setSubCompanyName(source.getSubCompanyName());
        target.setSubCountryCode(source.getSubCountryCode());
        target.setSubState(source.getSubState());
        target.setSubCity(source.getSubCity());
        target.setSubStreet(source.getSubStreet());
        target.setMerchantCategory(source.getMerchantCategory());
        target.setIntesCode(source.getIntesCode());
        target.setChargeType(source.getChargeType());
        return target;
    }

    private PaymentCreateVO.BillingInfoVO toBillingInfoVO(PaymentCreateClientResponseDTO responseDTO) {
        if (responseDTO.getLabelAmount() == null
                && !StringUtils.hasText(responseDTO.getLabelCurrency())
                && responseDTO.getTransactionAmount() == null
                && !StringUtils.hasText(responseDTO.getTransactionCurrency())
                && responseDTO.getTransactionRate() == null
                && !StringUtils.hasText(responseDTO.getRateSource())
                && responseDTO.getRateTime() == null
                && responseDTO.getSettlementAmount() == null
                && !StringUtils.hasText(responseDTO.getSettlementCurrency())) {
            return null;
        }
        PaymentCreateVO.BillingInfoVO billingInfoVO = new PaymentCreateVO.BillingInfoVO();
        billingInfoVO.setLabelAmount(responseDTO.getLabelAmount());
        billingInfoVO.setLabelCurrency(responseDTO.getLabelCurrency());
        billingInfoVO.setTransactionAmount(responseDTO.getTransactionAmount());
        billingInfoVO.setTransactionCurrency(responseDTO.getTransactionCurrency());
        billingInfoVO.setTransactionRate(normalizeRate(responseDTO.getTransactionRate()));
        billingInfoVO.setRateSource(responseDTO.getRateSource());
        billingInfoVO.setRateTime(toOffsetDateTime(responseDTO.getRateTime(), responseDTO.getTransactionTimeZone()));
        billingInfoVO.setSettlementAmount(responseDTO.getSettlementAmount());
        billingInfoVO.setSettlementCurrency(responseDTO.getSettlementCurrency());
        return billingInfoVO;
    }

    private BigDecimal normalizeRate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(8, RoundingMode.HALF_UP);
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime dateTime, String timeZone) {
        if (dateTime == null) {
            return null;
        }
        ZoneId zoneId = ZoneId.of(StringUtils.hasText(timeZone) ? timeZone : "Asia/Shanghai");
        return dateTime.atZone(zoneId).toOffsetDateTime();
    }

    /**
     * 转换子商户信息。
     *
     * @param source OpenAPI 子商户信息
     * @return 支付内部调用子商户信息
     */
    PaymentCreateClientRequestDTO.SubMerchantInfoDTO toPaymentClientSubMerchantInfo(ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO source);

    /**
     * 转换账单持卡人信息。
     *
     * @param source OpenAPI 账单持卡人信息
     * @return 支付内部调用账单持卡人信息
     */
    PaymentCreateClientRequestDTO.BillingCardHolderInfoDTO toPaymentClientBillingCardHolderInfo(ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO source);

    /**
     * 转换卡信息。
     * <p>
     * 卡号和安全码只允许在 OpenAPI 到 Payment 的内存链路中传递，禁止在日志、MQ 或数据库中明文保存。
     *
     * @param source OpenAPI 卡信息
     * @return 支付内部调用卡信息
     */
    PaymentCreateClientRequestDTO.CardInfoDTO toPaymentClientCardInfo(ApiMerchantPaymentRequestDTO.CardInfoDTO source);

    /**
     * 转换 3DS 认证信息。
     *
     * @param source OpenAPI 3DS 信息
     * @return 支付内部调用 3DS 信息
     */
    PaymentCreateClientRequestDTO.ThreeDsInfoDTO toPaymentClientThreeDsInfo(ApiMerchantPaymentRequestDTO.ThreeDsInfoDTO source);

    /**
     * 转换交易扩展信息。
     *
     * @param source OpenAPI 交易扩展信息
     * @return 支付内部调用交易扩展信息
     */
    @Mapping(target = "sourceTransactionDateTime", ignore = true)
    PaymentCreateClientRequestDTO.TransactionInfoDTO toPaymentClientTransactionInfo(ApiMerchantPaymentRequestDTO.TransactionInfoDTO source);
}
