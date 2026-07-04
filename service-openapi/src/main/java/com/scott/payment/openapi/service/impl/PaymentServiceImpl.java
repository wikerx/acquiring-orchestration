package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.config.PaymentClientProperties;
import com.scott.payment.openapi.converter.OpenApiRequestConverter;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
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
 * @description : 开放接口收单支付服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIPayment Service Impl，位于 service-openapi 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    /**
     * 平台收单订单号前缀，用于本地降级模式生成可追踪的模拟订单号。
     */
    private static final String PAYMENT_ORDER_PREFIX = "PA";

    /**
     * 交易已接收状态，表示 OpenAPI 请求已通过基础鉴权、解密和参数校验。
     */
    private static final String STATUS_RECEIVED = "RECEIVED";

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
     * 创建开放接口收单支付服务实现。
     *
     * @param converter               OpenAPI 请求转换器
     * @param paymentInternalClient   service-payment 内部调用客户端
     * @param paymentClientProperties 支付内部调用配置
     * @param keyMaterialFactory      OpenAPI 密钥材料工具
     */
    public PaymentServiceImpl(OpenApiRequestConverter converter,
                              PaymentInternalClient paymentInternalClient,
                              PaymentClientProperties paymentClientProperties,
                              OpenApiKeyMaterialFactory keyMaterialFactory,
                              OpenApiRequestContext requestContext) {
        this.converter = converter;
        this.paymentInternalClient = paymentInternalClient;
        this.paymentClientProperties = paymentClientProperties;
        this.keyMaterialFactory = keyMaterialFactory;
        this.requestContext = requestContext;
    }

    /**
     * 创建收单支付交易。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的统一请求参数
     * @return 创建交易响应
     */
    /**
     * 创建或保存商户 OpenAPI数据，保持请求校验、默认值和审计字段一致。
     * @param encryptedData 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
        vo.setStatus(STATUS_RECEIVED);
        return vo;
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
