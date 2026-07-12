package com.scott.payment.openapi.converter;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.dto.body.PaymentCreateRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
    @Mapping(target = "paymentOrderNo", ignore = true)
    @Mapping(target = "status", ignore = true)
    PaymentCreateVO toPaymentCreateVO(PaymentCreateRequestDTO requestDTO);

    /**
     * 将 service-payment 创建交易响应转换为商户 OpenAPI 响应。
     *
     * @param responseDTO 支付内部创建响应
     * @return 商户 OpenAPI 创建响应
     */
    PaymentCreateVO toPaymentCreateVO(PaymentCreateClientResponseDTO responseDTO);

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
        ApiMerchantPaymentRequestDTO.OrderInfoDTO orderInfo = requestDTO.getOrderInfo();
        vo.setMerchantOrderNo(orderInfo.getTradeNo());
        vo.setCurrency(orderInfo.getCurrency());
        return vo;
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
    PaymentCreateClientRequestDTO.TransactionInfoDTO toPaymentClientTransactionInfo(ApiMerchantPaymentRequestDTO.TransactionInfoDTO source);
}
