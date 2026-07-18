package com.scott.payment.openapi.converter;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.dto.body.PaymentCreateRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import com.scott.payment.openapi.vo.payment.PaymentQueryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

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
     * 交易失败时返回给商户的统一模糊描述，渠道真实失败原因只保存在后台日志和交易详情。
     */
    String TRANSACTION_DECLINED_MESSAGE = "The transaction was declined; please contact your card issuer or try again.";

    /**
     * 将普通收单创建 DTO 转换为创建响应。
     *
     * @param requestDTO 普通收单创建 DTO
     * @return 创建响应
     */
    @Mapping(target = "orderInfo", ignore = true)
    @Mapping(target = "transactionInfo", ignore = true)
    @Mapping(target = "merchantInfo", ignore = true)
    @Mapping(target = "billingCardHolderInfo", ignore = true)
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
     * 将商户交易请求和 payment 内部响应合并为创建类交易动作商户响应。
     * <p>
     * 创建类动作要求 merchantInfo、orderInfo、billingCardHolderInfo 以商户请求为准原样回显；
     * cardInfo 不参与响应，交易结果、平台交易 ID、卡品牌识别结果仍来自 payment 内部响应。
     *
     * @param requestDTO                  商户交易请求
     * @param responseDTO                 payment 内部响应
     * @param merchantSettlementCurrency 商户信息表中的结算币种
     * @return 商户交易响应
     */
    default PaymentCreateVO toPaymentCreateVO(ApiMerchantPaymentRequestDTO requestDTO,
                                              PaymentCreateClientResponseDTO responseDTO,
                                              String merchantSettlementCurrency) {
        PaymentCreateVO vo = toPaymentCreateVO(responseDTO);
        if (requestDTO == null) {
            overrideSettlementCurrency(vo, merchantSettlementCurrency);
            return vo;
        }
        vo.setMerchantInfo(toMerchantInfoVO(requestDTO.getMerchantInfo()));
        vo.setBillingCardHolderInfo(toBillingCardHolderInfoVO(requestDTO.getBillingCardHolderInfo()));
        if (requestDTO.getOrderInfo() != null) {
            PaymentCreateVO.OrderInfoVO orderInfoVO = vo.getOrderInfo() == null ? new PaymentCreateVO.OrderInfoVO() : vo.getOrderInfo();
            orderInfoVO.setOrderNo(requestDTO.getOrderInfo().getOrderNo());
            orderInfoVO.setOrderId(requestDTO.getOrderInfo().getOrderId());
            orderInfoVO.setAmount(requestDTO.getOrderInfo().getAmount());
            orderInfoVO.setCurrency(requestDTO.getOrderInfo().getCurrency());
            normalizeCreateActionTotals(orderInfoVO, responseDTO);
            vo.setOrderInfo(orderInfoVO);
        }
        if (requestDTO.getTransactionInfo() != null) {
            PaymentCreateVO.TransactionInfoVO transactionInfoVO = vo.getTransactionInfo() == null
                    ? new PaymentCreateVO.TransactionInfoVO() : vo.getTransactionInfo();
            transactionInfoVO.setDescription(requestDTO.getTransactionInfo().getDescription());
            transactionInfoVO.setCallbackUrl(requestDTO.getTransactionInfo().getCallbackUrl());
            vo.setTransactionInfo(transactionInfoVO);
        }
        normalizeMerchantFailureMessage(vo, responseDTO);
        overrideSettlementCurrency(vo, merchantSettlementCurrency);
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
            vo.setMerchantInfo(toMerchantInfoVO(requestDTO.getMerchantInfo()));
        }
        ApiMerchantPaymentRequestDTO.OrderInfoDTO orderInfo = requestDTO.getOrderInfo();
        PaymentCreateVO.OrderInfoVO orderInfoVO = new PaymentCreateVO.OrderInfoVO();
        orderInfoVO.setOrderNo(orderInfo.getOrderNo());
        orderInfoVO.setOrderId(orderInfo.getOrderId());
        orderInfoVO.setAmount(orderInfo.getAmount());
        orderInfoVO.setCurrency(orderInfo.getCurrency());
        vo.setOrderInfo(orderInfoVO);
        vo.setCurrency(orderInfo.getCurrency());
        vo.setBillingCardHolderInfo(toBillingCardHolderInfoVO(requestDTO.getBillingCardHolderInfo()));
        return vo;
    }

    /**
     * 将交易查询内部响应转换为商户查询响应。
     * <p>
     * 查询接口的 transactionInfo 是交易动作数组，不能复用创建类接口的单对象响应契约；merchantInfo 按商户请求原样回显。
     *
     * @param requestDTO  商户查询请求
     * @param responseDTO payment 内部查询响应
     * @return 商户查询响应
     */
    default PaymentQueryVO toPaymentQueryVO(ApiMerchantPaymentRequestDTO requestDTO,
                                            PaymentQueryClientResponseDTO responseDTO) {
        PaymentQueryVO vo = new PaymentQueryVO();
        vo.setMerchantInfo(requestDTO == null ? null : toMerchantInfoVO(requestDTO.getMerchantInfo()));
        PaymentCreateVO.OrderInfoVO orderInfoVO = new PaymentCreateVO.OrderInfoVO();
        if (requestDTO != null && requestDTO.getOrderInfo() != null) {
            orderInfoVO.setOrderNo(requestDTO.getOrderInfo().getOrderNo());
            orderInfoVO.setOrderId(requestDTO.getOrderInfo().getOrderId());
        }
        if (responseDTO != null) {
            orderInfoVO.setAmount(responseDTO.getOrderAmount());
            orderInfoVO.setCurrency(responseDTO.getOrderCurrency());
            orderInfoVO.setTotalAuthorizedAmount(responseDTO.getTotalAuthorizedAmount());
            orderInfoVO.setTotalCapturedAmount(responseDTO.getTotalCapturedAmount());
            orderInfoVO.setTotalRefundAmount(responseDTO.getTotalRefundAmount());
            orderInfoVO.setTotalVoidAmount(responseDTO.getTotalVoidAmount());
            orderInfoVO.setTotalChargebackAmount(responseDTO.getTotalChargebackAmount());
            vo.setBillingInfo(toBillingInfoVO(responseDTO));
            vo.setTransactionInfo((responseDTO.getTransactionInfo() == null ? List.<PaymentQueryClientResponseDTO.TransactionInfoDTO>of()
                    : responseDTO.getTransactionInfo()).stream()
                    .map(item -> toQueryTransactionInfoVO(item, responseDTO.getTransactionTimeZone()))
                    .toList());
        }
        vo.setOrderInfo(orderInfoVO);
        return vo;
    }

    private PaymentCreateVO.MerchantInfoVO toMerchantInfoVO(ApiMerchantPaymentRequestDTO.MerchantInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateVO.MerchantInfoVO merchantInfoVO = new PaymentCreateVO.MerchantInfoVO();
        merchantInfoVO.setMerchantId(source.getMerchantId());
        merchantInfoVO.setSubMerchantInfo(toSubMerchantInfoVO(source.getSubMerchantInfo()));
        return merchantInfoVO;
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
        target.setSubPostal(source.getSubPostal());
        target.setSubEmail(source.getSubEmail());
        target.setSubPhone(source.getSubPhone());
        target.setSubTaxId(source.getSubTaxId());
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
        target.setSubPostal(source.getSubPostal());
        target.setSubEmail(source.getSubEmail());
        target.setSubPhone(source.getSubPhone());
        target.setSubTaxId(source.getSubTaxId());
        target.setMerchantCategory(source.getMerchantCategory());
        target.setIntesCode(source.getIntesCode());
        target.setChargeType(source.getChargeType());
        return target;
    }

    private PaymentCreateVO.BillingCardHolderInfoVO toBillingCardHolderInfoVO(ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateVO.BillingCardHolderInfoVO target = new PaymentCreateVO.BillingCardHolderInfoVO();
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

    private void overrideSettlementCurrency(PaymentCreateVO vo, String merchantSettlementCurrency) {
        if (!StringUtils.hasText(merchantSettlementCurrency)) {
            return;
        }
        PaymentCreateVO.BillingInfoVO billingInfoVO = vo.getBillingInfo() == null
                ? new PaymentCreateVO.BillingInfoVO() : vo.getBillingInfo();
        billingInfoVO.setSettlementCurrency(merchantSettlementCurrency);
        vo.setBillingInfo(billingInfoVO);
    }

    private void normalizeCreateActionTotals(PaymentCreateVO.OrderInfoVO orderInfoVO, PaymentCreateClientResponseDTO responseDTO) {
        if (orderInfoVO == null || responseDTO == null || !isInitialCreateAction(responseDTO.getTransactionType())) {
            return;
        }
        if ("SUCCESS".equals(responseDTO.getStatus()) && "PAYMENT".equals(responseDTO.getTransactionType())) {
            orderInfoVO.setTotalAuthorizedAmount(orderInfoVO.getAmount());
            orderInfoVO.setTotalCapturedAmount(orderInfoVO.getAmount());
            orderInfoVO.setTotalRefundAmount(defaultZero(orderInfoVO.getTotalRefundAmount()));
            return;
        }
        if ("SUCCESS".equals(responseDTO.getStatus())) {
            orderInfoVO.setTotalAuthorizedAmount(orderInfoVO.getAmount());
            orderInfoVO.setTotalCapturedAmount(BigDecimal.ZERO);
            orderInfoVO.setTotalRefundAmount(BigDecimal.ZERO);
            return;
        }
        if ("FAILED".equals(responseDTO.getStatus())) {
            orderInfoVO.setTotalAuthorizedAmount(BigDecimal.ZERO);
            orderInfoVO.setTotalCapturedAmount(BigDecimal.ZERO);
            orderInfoVO.setTotalRefundAmount(BigDecimal.ZERO);
        }
    }

    private void normalizeMerchantFailureMessage(PaymentCreateVO vo, PaymentCreateClientResponseDTO responseDTO) {
        if (vo == null || vo.getTransactionInfo() == null || responseDTO == null) {
            return;
        }
        if (isInitialCreateAction(responseDTO.getTransactionType()) && "FAILED".equals(responseDTO.getStatus())) {
            vo.getTransactionInfo().setMessage(TRANSACTION_DECLINED_MESSAGE);
        }
    }

    private boolean isInitialCreateAction(String transactionType) {
        return "PAYMENT".equals(transactionType)
                || "AUTHORIZATION".equals(transactionType)
                || "PRE_AUTHORIZATION".equals(transactionType);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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

    private PaymentCreateVO.BillingInfoVO toBillingInfoVO(PaymentQueryClientResponseDTO responseDTO) {
        if (responseDTO == null
                || (responseDTO.getLabelAmount() == null
                && !StringUtils.hasText(responseDTO.getLabelCurrency())
                && responseDTO.getTransactionAmount() == null
                && !StringUtils.hasText(responseDTO.getTransactionCurrency())
                && responseDTO.getTransactionRate() == null
                && !StringUtils.hasText(responseDTO.getRateSource())
                && responseDTO.getRateTime() == null
                && responseDTO.getSettlementAmount() == null
                && !StringUtils.hasText(responseDTO.getSettlementCurrency()))) {
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

    private PaymentQueryVO.TransactionInfoVO toQueryTransactionInfoVO(PaymentQueryClientResponseDTO.TransactionInfoDTO source,
                                                                      String timeZone) {
        PaymentQueryVO.TransactionInfoVO target = new PaymentQueryVO.TransactionInfoVO();
        if (source == null) {
            return target;
        }
        target.setTransactionId(source.getTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setCode(source.getCode());
        target.setMessage(source.getMessage());
        target.setTransactionType(source.getTransactionType());
        target.setTransactionDateTime(toOffsetDateTime(source.getTransactionDateTime(), timeZone));
        target.setPaymentMethod(source.getPaymentMethod());
        target.setCardBrand(source.getCardBrand());
        target.setCardBin(source.getCardBin());
        target.setAuthCode(source.getAuthCode());
        target.setArn(source.getArn());
        target.setDescription(source.getDescription());
        target.setCallbackUrl(source.getCallbackUrl());
        return target;
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
