package com.scott.payment.openapi.converter;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.dto.body.PaymentCreateRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import com.scott.payment.openapi.vo.payment.PaymentQueryVO;
import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiMessageLocale;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionProjection;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionResultProjector;
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
 * 商户 OpenAPI 请求对象转换器，位于 service-openapi 转换层，只做字段映射，不承担币种金额精度换算。
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
    @Mapping(target = "billingCardHolderInfo", ignore = true)
    @Mapping(target = "goodsInfo", ignore = true)
    @Mapping(target = "payerInfo", ignore = true)
    @Mapping(target = "shippingInfo", ignore = true)
    @Mapping(target = "threeDSInfo", ignore = true)
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
        vo.setGoodsInfo(toGoodsInfoVO(responseDTO.getGoodsInfo()));
        vo.setBillingCardHolderInfo(toBillingCardHolderInfoVO(responseDTO.getBillingCardHolderInfo()));
        vo.setPayerInfo(toPayerInfoVO(responseDTO.getPayerInfo()));
        vo.setShippingInfo(toShippingInfoVO(responseDTO.getShippingInfo()));
        vo.setThreeDSInfo(toThreeDsInfoVO(responseDTO.getThreeDSInfo()));

        PaymentCreateVO.OrderInfoVO orderInfoVO = new PaymentCreateVO.OrderInfoVO();
        orderInfoVO.setOrderNo(responseDTO.getMerchantOrderNo());
        orderInfoVO.setOrderId(responseDTO.getMerchantOrderId());
        orderInfoVO.setAmount(responseDTO.getOrderAmount());
        orderInfoVO.setCurrency(responseDTO.getOrderCurrency());
        orderInfoVO.setTotalAuthorizedAmount(responseDTO.getTotalAuthorizedAmount());
        orderInfoVO.setTotalCapturedAmount(responseDTO.getTotalCapturedAmount());
        orderInfoVO.setTotalRefundAmount(responseDTO.getTotalRefundAmount());
        orderInfoVO.setTotalAuthorizedCancelAmount(responseDTO.getTotalAuthorizedCancelAmount());
        orderInfoVO.setTotalRefuseAmount(responseDTO.getTotalRefuseAmount());
        vo.setOrderInfo(orderInfoVO);

        PaymentCreateVO.TransactionInfoVO transactionInfoVO = new PaymentCreateVO.TransactionInfoVO();
        transactionInfoVO.setTransactionId(responseDTO.getTransactionId());
        transactionInfoVO.setSourceTransactionId(responseDTO.getSourceTransactionId());
        transactionInfoVO.setSourceTransactionDateTime(toOffsetDateTime(
                responseDTO.getSourceTransactionDateTime(), responseDTO.getTransactionTimeZone()));
        transactionInfoVO.setTransactionType(responseDTO.getTransactionType());
        transactionInfoVO.setTransactionStatus(responseDTO.getStatus());
        transactionInfoVO.setProcessStage(responseDTO.getProcessStage());
        transactionInfoVO.setTransactionDateTime(toOffsetDateTime(responseDTO.getTransactionDateTime(), responseDTO.getTransactionTimeZone()));
        transactionInfoVO.setRootTransactionDateTime(toOffsetDateTime(
                responseDTO.getRootTransactionDateTime(), responseDTO.getTransactionTimeZone()));
        transactionInfoVO.setPaymentMethod(responseDTO.getPaymentMethod());
        transactionInfoVO.setCardBrand(responseDTO.getPaymentBrand());
        transactionInfoVO.setCardBin(responseDTO.getCardBin());
        transactionInfoVO.setAuthCode(responseDTO.getAuthCode());
        transactionInfoVO.setArn(responseDTO.getAcquirerReferenceNo());
        transactionInfoVO.setDescription(responseDTO.getDescription());
        transactionInfoVO.setCallbackUrl(responseDTO.getCallbackUrl());
        transactionInfoVO.setMerchantWebsite(responseDTO.getMerchantWebsite());
        transactionInfoVO.setRedirectUrl(responseDTO.getRedirectUrl());
        transactionInfoVO.setLanguage(responseDTO.getLanguage());
        transactionInfoVO.setPendingReasonCode(responseDTO.getPendingReasonCode());
        applyTransactionProjection(transactionInfoVO,
                responseDTO.getStatus(),
                responseDTO.getProcessStage(),
                responseDTO.getFailReasonCode(),
                responseDTO.getPendingReasonCode(),
                responseDTO.getMerchantResponseCode());
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
        if (vo.getBillingCardHolderInfo() == null) {
            vo.setBillingCardHolderInfo(toBillingCardHolderInfoVO(requestDTO.getBillingCardHolderInfo()));
        }
        if (requestDTO.getOrderInfo() != null) {
            PaymentCreateVO.OrderInfoVO orderInfoVO = vo.getOrderInfo() == null ? new PaymentCreateVO.OrderInfoVO() : vo.getOrderInfo();
            orderInfoVO.setOrderNo(requestDTO.getOrderInfo().getOrderNo());
            orderInfoVO.setOrderId(requestDTO.getOrderInfo().getOrderId());
            orderInfoVO.setAmount(requestDTO.getOrderInfo().getAmount());
            if (StringUtils.hasText(requestDTO.getOrderInfo().getCurrency())) {
                orderInfoVO.setCurrency(requestDTO.getOrderInfo().getCurrency());
            }
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
        vo.setMerchantInfo(responseDTO == null
                ? requestDTO == null ? null : toMerchantInfoVO(requestDTO.getMerchantInfo())
                : toMerchantInfoVO(responseDTO));
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
            orderInfoVO.setTotalAuthorizedCancelAmount(responseDTO.getTotalAuthorizedCancelAmount());
            orderInfoVO.setTotalRefuseAmount(responseDTO.getTotalRefuseAmount());
            vo.setBillingInfo(toBillingInfoVO(responseDTO));
            vo.setGoodsInfo(toGoodsInfoVO(responseDTO.getGoodsInfo()));
            vo.setBillingCardHolderInfo(toBillingCardHolderInfoVO(responseDTO.getBillingCardHolderInfo()));
            vo.setPayerInfo(toPayerInfoVO(responseDTO.getPayerInfo()));
            vo.setShippingInfo(toShippingInfoVO(responseDTO.getShippingInfo()));
            vo.setThreeDSInfo(toThreeDsInfoVO(responseDTO.getThreeDSInfo()));
            vo.setTransactionInfo((responseDTO.getTransactionInfo() == null ? List.<PaymentQueryClientResponseDTO.TransactionInfoDTO>of()
                    : responseDTO.getTransactionInfo()).stream()
                    .map(item -> toQueryTransactionInfoVO(item, responseDTO.getTransactionTimeZone()))
                    .toList());
        }
        vo.setOrderInfo(orderInfoVO);
        return vo;
    }

    /**
     * 组装查询响应商户信息，子商户必须来自首次交易冻结快照。
     */
    private PaymentCreateVO.MerchantInfoVO toMerchantInfoVO(PaymentQueryClientResponseDTO source) {
        if (source == null || (!StringUtils.hasText(source.getMerchantId()) && source.getSubMerchantInfo() == null)) {
            return null;
        }
        PaymentCreateVO.MerchantInfoVO target = new PaymentCreateVO.MerchantInfoVO();
        target.setMerchantId(source.getMerchantId());
        target.setSubMerchantInfo(toSubMerchantInfoVO(source.getSubMerchantInfo()));
        return target;
    }

    /**
     * 将商户请求中的商户号和子商户快照转换为响应视图。
     *
     * @param source 商户 OpenAPI 请求中的商户信息
     * @return 商户响应视图；来源为空时返回 {@code null}
     */
    private PaymentCreateVO.MerchantInfoVO toMerchantInfoVO(ApiMerchantPaymentRequestDTO.MerchantInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateVO.MerchantInfoVO merchantInfoVO = new PaymentCreateVO.MerchantInfoVO();
        merchantInfoVO.setMerchantId(source.getMerchantId());
        merchantInfoVO.setSubMerchantInfo(toSubMerchantInfoVO(source.getSubMerchantInfo()));
        return merchantInfoVO;
    }

    /**
     * 将支付核心响应中的商户号和冻结子商户快照转换为响应视图。
     *
     * @param responseDTO 支付核心交易响应
     * @return 商户响应视图；商户信息全部缺失时返回 {@code null}
     */
    private PaymentCreateVO.MerchantInfoVO toMerchantInfoVO(PaymentCreateClientResponseDTO responseDTO) {
        if (!StringUtils.hasText(responseDTO.getMerchantId()) && responseDTO.getSubMerchantInfo() == null) {
            return null;
        }
        PaymentCreateVO.MerchantInfoVO merchantInfoVO = new PaymentCreateVO.MerchantInfoVO();
        merchantInfoVO.setMerchantId(responseDTO.getMerchantId());
        merchantInfoVO.setSubMerchantInfo(toSubMerchantInfoVO(responseDTO.getSubMerchantInfo()));
        return merchantInfoVO;
    }

    /**
     * 将支付核心冻结的子商户快照转换为对外响应视图。
     *
     * @param source 支付核心子商户快照
     * @return 子商户响应视图；来源为空时返回 {@code null}
     */
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

    /**
     * 将商户请求中的子商户信息转换为对外响应视图。
     *
     * @param source 商户请求子商户信息
     * @return 子商户响应视图；来源为空时返回 {@code null}
     */
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

    /**
     * 将 payment 查询响应中的子商户快照转换为商户响应。
     */
    private PaymentCreateVO.SubMerchantInfoVO toSubMerchantInfoVO(
            PaymentCreateClientRequestDTO.SubMerchantInfoDTO source) {
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

    /**
     * 将商户请求中的账单持卡人信息转换为响应视图。
     *
     * @param source 商户请求账单持卡人信息
     * @return 账单持卡人响应视图；来源为空时返回 {@code null}
     */
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

    /**
     * Convert the payment-service billing snapshot to the merchant response model.
     */
    private PaymentCreateVO.BillingCardHolderInfoVO toBillingCardHolderInfoVO(
            PaymentCreateClientRequestDTO.BillingCardHolderInfoDTO source) {
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

    /**
     * Convert product snapshot rows in their original order.
     */
    private List<PaymentCreateVO.GoodsInfoVO> toGoodsInfoVO(
            List<PaymentCreateClientRequestDTO.GoodsInfoDTO> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream().map(item -> {
            PaymentCreateVO.GoodsInfoVO target = new PaymentCreateVO.GoodsInfoVO();
            target.setName(item.getName());
            target.setQuantity(item.getQuantity());
            target.setAmount(item.getAmount());
            target.setCurrency(item.getCurrency());
            return target;
        }).toList();
    }

    /**
     * Convert the decrypted payer snapshot to the merchant response model.
     */
    private PaymentCreateVO.PayerInfoVO toPayerInfoVO(PaymentCreateClientRequestDTO.PayerInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateVO.PayerInfoVO target = new PaymentCreateVO.PayerInfoVO();
        target.setPayerId(source.getPayerId());
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setPhone(source.getPhone());
        target.setEmail(source.getEmail());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        target.setIpAddress(source.getIpAddress());
        target.setSessionId(source.getSessionId());
        target.setBrowserInfo(source.getBrowserInfo());
        target.setUserAgent(source.getUserAgent());
        return target;
    }

    /**
     * Convert the shipping snapshot to the merchant response model.
     */
    private PaymentCreateVO.ShippingInfoVO toShippingInfoVO(PaymentCreateClientRequestDTO.ShippingInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateVO.ShippingInfoVO target = new PaymentCreateVO.ShippingInfoVO();
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

    /**
     * 把可信商户配置中的结算币种写入 OpenAPI 账单响应，不使用请求参数覆盖。
     * @param vo 待组装或返回的接口视图对象，只允许写入调用方有权查看的字段
     * @param merchantSettlementCurrency 币种代码，格式为 ISO 4217 三位大写字母
     */
    private void overrideSettlementCurrency(PaymentCreateVO vo, String merchantSettlementCurrency) {
        if (!StringUtils.hasText(merchantSettlementCurrency)) {
            return;
        }
        PaymentCreateVO.BillingInfoVO billingInfoVO = vo.getBillingInfo() == null
                ? new PaymentCreateVO.BillingInfoVO() : vo.getBillingInfo();
        billingInfoVO.setSettlementCurrency(merchantSettlementCurrency);
        vo.setBillingInfo(billingInfoVO);
    }

    /**
     * 按初始支付、授权或预授权终态归一累计授权、请款和退款金额。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param orderInfoVO 待补齐累计金额的订单响应
     * @param responseDTO 支付核心初始交易响应
     */
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

    /**
     * 判断交易类型是否属于支付、授权或预授权等初始创建动作。
     * <p>
     * 纯判断操作，不修改业务状态。
     * </p>
     * @param transactionType 交易类型，取值来自平台交易类型枚举并决定状态机和渠道能力
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isInitialCreateAction(String transactionType) {
        return "PAYMENT".equals(transactionType)
                || "AUTHORIZATION".equals(transactionType)
                || "PRE_AUTHORIZATION".equals(transactionType);
    }

    /**
     * 将缺失金额转换为零，避免累计金额响应出现不一致的空值。
     *
     * @param value 支付核心累计金额
     * @return 原金额或 {@link BigDecimal#ZERO}
     */
    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 将创建类交易的标签金额、渠道交易金额、汇率和费用转换为账单响应。
     *
     * @param responseDTO 支付核心创建类交易响应
     * @return 账单响应；所有金额和汇率字段均为空时返回 {@code null}
     */
    private PaymentCreateVO.BillingInfoVO toBillingInfoVO(PaymentCreateClientResponseDTO responseDTO) {
        if (responseDTO.getLabelAmount() == null
                && !StringUtils.hasText(responseDTO.getLabelCurrency())
                && responseDTO.getTransactionAmount() == null
                && !StringUtils.hasText(responseDTO.getTransactionCurrency())
                && responseDTO.getTransactionRate() == null
                && !StringUtils.hasText(responseDTO.getRateSource())
                && responseDTO.getRateTime() == null
                && responseDTO.getSettlementRate() == null
                && responseDTO.getSettlementAmount() == null
                && !StringUtils.hasText(responseDTO.getSettlementCurrency())
                && responseDTO.getSettlementFeeAmount() == null
                && (responseDTO.getFeeItems() == null || responseDTO.getFeeItems().isEmpty())) {
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
        billingInfoVO.setSettlementRate(normalizeRate(responseDTO.getSettlementRate()));
        billingInfoVO.setSettlementAmount(responseDTO.getSettlementAmount());
        billingInfoVO.setSettlementCurrency(responseDTO.getSettlementCurrency());
        billingInfoVO.setSettlementFeeAmount(responseDTO.getSettlementFeeAmount());
        billingInfoVO.setFeeItems(toFeeItemVO(responseDTO.getFeeItems()));
        return billingInfoVO;
    }

    /**
     * 将查询聚合中的标签金额、渠道交易金额、汇率和费用转换为账单响应。
     *
     * @param responseDTO 支付核心订单查询响应
     * @return 账单响应；所有金额和汇率字段均为空时返回 {@code null}
     */
    private PaymentCreateVO.BillingInfoVO toBillingInfoVO(PaymentQueryClientResponseDTO responseDTO) {
        if (responseDTO == null
                || (responseDTO.getLabelAmount() == null
                && !StringUtils.hasText(responseDTO.getLabelCurrency())
                && responseDTO.getTransactionAmount() == null
                && !StringUtils.hasText(responseDTO.getTransactionCurrency())
                && responseDTO.getTransactionRate() == null
                && !StringUtils.hasText(responseDTO.getRateSource())
                && responseDTO.getRateTime() == null
                && responseDTO.getSettlementRate() == null
                && responseDTO.getSettlementAmount() == null
                && !StringUtils.hasText(responseDTO.getSettlementCurrency())
                && responseDTO.getSettlementFeeAmount() == null
                && (responseDTO.getFeeItems() == null || responseDTO.getFeeItems().isEmpty()))) {
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
        billingInfoVO.setSettlementRate(normalizeRate(responseDTO.getSettlementRate()));
        billingInfoVO.setSettlementAmount(responseDTO.getSettlementAmount());
        billingInfoVO.setSettlementCurrency(responseDTO.getSettlementCurrency());
        billingInfoVO.setSettlementFeeAmount(responseDTO.getSettlementFeeAmount());
        billingInfoVO.setFeeItems(toFeeItemVO(responseDTO.getFeeItems()));
        return billingInfoVO;
    }

    /**
     * 转换商户可见的 3DS 安全字段白名单。
     */
    private PaymentCreateVO.ThreeDsInfoVO toThreeDsInfoVO(
            PaymentCreateClientResponseDTO.ThreeDsInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateVO.ThreeDsInfoVO target = new PaymentCreateVO.ThreeDsInfoVO();
        target.setEci(source.getEci());
        target.setDsTransactionId(source.getDsTransactionId());
        target.setThreeDsVersion(source.getThreeDsVersion());
        target.setStatus(source.getStatus());
        target.setLiabilityShifted(source.getLiabilityShifted());
        return target;
    }

    /**
     * 转换已形成的费用明细，不在 OpenAPI 层重新计算金额或汇率。
     */
    private List<PaymentCreateVO.FeeItemVO> toFeeItemVO(
            List<PaymentCreateClientResponseDTO.FeeItemDTO> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return source.stream().map(item -> {
            PaymentCreateVO.FeeItemVO target = new PaymentCreateVO.FeeItemVO();
            target.setCategories(item.getCategories());
            target.setAmount(item.getAmount());
            target.setCurrency(item.getCurrency());
            target.setRate(normalizeRate(item.getRate()));
            return target;
        }).toList();
    }

    /**
     * 将单笔支付核心交易事实转换为查询响应，并统一投影公开状态码和文案。
     *
     * @param source 支付核心单笔交易事实
     * @param timeZone 商户交易时区，用于解释无偏移本地时间
     * @return 单笔交易查询响应；来源为空时返回空响应对象
     */
    private PaymentQueryVO.TransactionInfoVO toQueryTransactionInfoVO(PaymentQueryClientResponseDTO.TransactionInfoDTO source,
                                                                      String timeZone) {
        PaymentQueryVO.TransactionInfoVO target = new PaymentQueryVO.TransactionInfoVO();
        if (source == null) {
            return target;
        }
        target.setTransactionId(source.getTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setSourceTransactionDateTime(toOffsetDateTime(source.getSourceTransactionDateTime(), timeZone));
        target.setTransactionType(source.getTransactionType());
        target.setTransactionStatus(source.getTransactionStatus());
        target.setProcessStage(source.getProcessStage());
        target.setPendingReasonCode(source.getPendingReasonCode());
        applyTransactionProjection(target,
                source.getTransactionStatus(),
                source.getProcessStage(),
                source.getFailReasonCode(),
                source.getPendingReasonCode(),
                source.getCode());
        target.setTransactionDateTime(toOffsetDateTime(source.getTransactionDateTime(), timeZone));
        target.setRootTransactionDateTime(toOffsetDateTime(source.getRootTransactionDateTime(), timeZone));
        target.setPaymentMethod(source.getPaymentMethod());
        target.setCardBrand(source.getCardBrand());
        target.setCardBin(source.getCardBin());
        target.setAuthCode(source.getAuthCode());
        target.setArn(source.getArn());
        target.setDescription(source.getDescription());
        target.setCallbackUrl(source.getCallbackUrl());
        target.setMerchantWebsite(source.getMerchantWebsite());
        target.setRedirectUrl(source.getRedirectUrl());
        target.setLanguage(source.getLanguage());
        return target;
    }

    /**
     * 将支付核心事实投影到创建类交易响应，公开原因始终使用平台固定模板。
     *
     * @param target 商户创建类交易信息
     * @param status 支付核心状态
     * @param processStage 支付核心处理阶段
     * @param failureReasonCode 支付核心受控失败原因码
     * @param pendingReasonCode 支付核心受控等待原因码
     * @param legacyMerchantCode 历史内部响应码，仅用于兼容输入
     */
    private void applyTransactionProjection(PaymentCreateVO.TransactionInfoVO target,
                                            String status,
                                            String processStage,
                                            String failureReasonCode,
                                            String pendingReasonCode,
                                            String legacyMerchantCode) {
        OpenApiTransactionProjection projection = OpenApiTransactionResultProjector.project(
                status, processStage, failureReasonCode, pendingReasonCode, legacyMerchantCode);
        target.setCode(projection.resultCode().getCode());
        target.setMessage(projection.message(OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        target.setFailReasonCode(projection.failureReasonCode());
        target.setFailReasonMessage(projection.failureReasonMessage());
    }

    /**
     * 查询响应与创建响应共享同一投影规则，避免相同交易事实产生不同公开结果。
     */
    private void applyTransactionProjection(PaymentQueryVO.TransactionInfoVO target,
                                            String status,
                                            String processStage,
                                            String failureReasonCode,
                                            String pendingReasonCode,
                                            String legacyMerchantCode) {
        OpenApiTransactionProjection projection = OpenApiTransactionResultProjector.project(
                status, processStage, failureReasonCode, pendingReasonCode, legacyMerchantCode);
        target.setCode(projection.resultCode().getCode());
        target.setMessage(projection.message(OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        target.setFailReasonCode(projection.failureReasonCode());
        target.setFailReasonMessage(projection.failureReasonMessage());
    }

    /**
     * 将非空汇率统一保留八位小数并使用 {@link RoundingMode#HALF_UP} 舍入。
     *
     * @param rate 支付核心已冻结的交易或结算汇率
     * @return 八位小数汇率；输入为空时返回 {@code null}
     */
    private BigDecimal normalizeRate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 按指定 IANA 时区把支付核心本地时间转换为带 UTC 偏移的时间。
     *
     * @param dateTime 支付核心保存的本地日期时间
     * @param timeZone 商户交易时区；为空时使用平台默认时区
     * @return 带偏移的交易时间；输入为空时返回 {@code null}
     */
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
     * 转换首次交易商品明细。
     */
    List<PaymentCreateClientRequestDTO.GoodsInfoDTO> toPaymentClientGoodsInfo(List<ApiMerchantPaymentRequestDTO.GoodsInfoDTO> source);

    /**
     * 转换付款人身份和浏览器上下文。
     */
    PaymentCreateClientRequestDTO.PayerInfoDTO toPaymentClientPayerInfo(ApiMerchantPaymentRequestDTO.PayerInfoDTO source);

    /**
     * 转换收货人及收货地址。
     */
    PaymentCreateClientRequestDTO.ShippingInfoDTO toPaymentClientShippingInfo(ApiMerchantPaymentRequestDTO.ShippingInfoDTO source);

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
    @Mapping(target = "cardBrand", ignore = true)
    @Mapping(target = "sourceTransactionDateTime", ignore = true)
    @Mapping(target = "rootTransactionDateTime", ignore = true)
    PaymentCreateClientRequestDTO.TransactionInfoDTO toPaymentClientTransactionInfo(ApiMerchantPaymentRequestDTO.TransactionInfoDTO source);

    /**
     * 将带偏移的商户请求时间统一转换为支付数据库业务时区，避免调用方时区改变季度路由。
     *
     * @param source 商户请求时间
     * @return Asia/Shanghai 本地业务时间；输入为空时返回 null
     */
    default LocalDateTime toLocalDateTime(OffsetDateTime source) {
        return source == null ? null : source.atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }

    /**
     * 转换商户实时风控上下文。
     *
     * @param source 商户请求中的风控上下文
     * @return payment 内部请求风控上下文
     */
    PaymentCreateClientRequestDTO.RiskContextDTO toPaymentClientRiskContext(ApiMerchantPaymentRequestDTO.RiskInfoDTO source);
}
