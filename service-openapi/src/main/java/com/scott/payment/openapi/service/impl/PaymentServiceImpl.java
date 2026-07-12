package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.config.PaymentClientProperties;
import com.scott.payment.openapi.converter.OpenApiRequestConverter;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.enums.OpenApiPaymentStatusEnum;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentServiceImpl
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 收单支付服务实现，位于 service-openapi 服务层，负责本地降级受理和转调 service-payment。
 * @status : create
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    /**
     * 平台收单订单号前缀，用于本地降级模式生成可追踪的模拟订单号。
     */
    private static final String PAYMENT_ORDER_PREFIX = "PA";

    /**
     * OpenAPI 请求转换器，负责把外部公共请求 DTO 转换成当前接口响应或内部服务对象。
     */
    private final OpenApiRequestConverter converter;

    /**
     * service-payment 内部调用客户端，负责完成 OpenAPI 到支付核心服务的微服务调用。
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * 支付内部调用配置，用于测试或本地模式切换远程调用。
     */
    private final PaymentClientProperties paymentClientProperties;

    /**
     * OpenAPI 密钥材料工具，用于计算密文指纹，避免把完整密文或卡信息传入日志。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    /**
     * OpenAPI 请求上下文访问器。
     */
    private final OpenApiRequestContext requestContext;

    /**
     * ISO 币种字典服务，用于本地降级模式按币种精度转换响应金额。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 创建开放接口收单支付服务实现。
     *
     * @param converter               OpenAPI 请求转换器
     * @param paymentInternalClient   service-payment 内部调用客户端
     * @param paymentClientProperties 支付内部调用配置
     * @param keyMaterialFactory      OpenAPI 密钥材料工具
     * @param requestContext          OpenAPI 请求上下文访问器
     * @param isoDictionaryService    ISO 币种字典服务
     */
    public PaymentServiceImpl(OpenApiRequestConverter converter,
                              PaymentInternalClient paymentInternalClient,
                              PaymentClientProperties paymentClientProperties,
                              OpenApiKeyMaterialFactory keyMaterialFactory,
                              OpenApiRequestContext requestContext,
                              IsoDictionaryService isoDictionaryService) {
        this.converter = converter;
        this.paymentInternalClient = paymentInternalClient;
        this.paymentClientProperties = paymentClientProperties;
        this.keyMaterialFactory = keyMaterialFactory;
        this.requestContext = requestContext;
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 创建收单支付交易。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的统一请求参数
     * @return 创建交易响应
     */
    @Override
    public PaymentCreateVO createPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        if (!paymentClientProperties.isRemoteEnabled()) {
            return createLocalPaymentResult(requestDTO);
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO);
        PaymentCreateClientResponseDTO clientResponseDTO = paymentInternalClient.createAuthorization(clientRequestDTO);
        return toPaymentCreateVO(clientResponseDTO);
    }

    /**
     * 构建本地降级模式支付响应。
     * <p>
     * 单元测试或本地只启动 `service-openapi` 时不依赖 `service-payment`，但仍然返回平台订单号和状态，
     * 方便商户侧完整验证响应解析、日志追踪和后续回调字段映射。
     *
     * @param requestDTO 解密后的统一请求参数
     * @return 本地模拟创建交易响应
     */
    private PaymentCreateVO createLocalPaymentResult(ApiMerchantPaymentRequestDTO requestDTO) {
        PaymentCreateVO vo = converter.toPaymentCreateVO(requestDTO);
        vo.setPaymentOrderNo(PaymentOrderNoGenerator.nextOrderNo(PAYMENT_ORDER_PREFIX));
        vo.setStatus(OpenApiPaymentStatusEnum.RECEIVED.getCode());
        vo.setAmount(toMinorAmount(requestDTO));
        return vo;
    }

    /**
     * 本地降级模式按 ISO 4217 默认辅币位转换响应金额。
     *
     * @param requestDTO 解密后的统一请求参数
     * @return 最小辅币单位金额
     */
    private Long toMinorAmount(ApiMerchantPaymentRequestDTO requestDTO) {
        try {
            return isoDictionaryService.toMinorUnit(requestDTO.getOrderInfo().getAmount(), requestDTO.getOrderInfo().getCurrency());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 构建调用 service-payment 的内部请求。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的统一请求参数
     * @return 支付内部创建请求
     */
    private PaymentCreateClientRequestDTO toPaymentClientRequest(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        PaymentCreateClientRequestDTO clientRequestDTO = new PaymentCreateClientRequestDTO();
        clientRequestDTO.setMerchantId(requestContext.getRequiredMerchantId());
        clientRequestDTO.setMerchantOrderNo(requestDTO.getOrderInfo().getTradeNo());
        clientRequestDTO.setAmount(requestDTO.getOrderInfo().getAmount());
        clientRequestDTO.setCurrency(requestDTO.getOrderInfo().getCurrency());
        clientRequestDTO.setTransactionDateTime(LocalDateTime.now());
        if (requestDTO.getTransactionInfo() != null) {
            clientRequestDTO.setRequestId(requestDTO.getTransactionInfo().getTransactionId());
        }
        clientRequestDTO.setRequestFingerprint(keyMaterialFactory.fingerprint(encryptedData));
        return clientRequestDTO;
    }

    /**
     * 转换 service-payment 内部响应为商户 OpenAPI 响应。
     *
     * @param clientResponseDTO 支付内部创建响应
     * @return OpenAPI 创建响应
     */
    private PaymentCreateVO toPaymentCreateVO(PaymentCreateClientResponseDTO clientResponseDTO) {
        PaymentCreateVO vo = new PaymentCreateVO();
        vo.setPaymentOrderNo(clientResponseDTO.getPaymentOrderNo());
        vo.setMerchantOrderNo(clientResponseDTO.getMerchantOrderNo());
        vo.setStatus(clientResponseDTO.getStatus());
        vo.setCurrency(clientResponseDTO.getCurrency());
        vo.setAmount(clientResponseDTO.getAmount());
        return vo;
    }
}
