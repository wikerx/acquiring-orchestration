package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payout.PayoutInternalClient;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;
import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.service.PayoutService;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 将商户代付请求转换为内部命令并调用代付核心；OpenAPI 层不持有代付状态机和资金事实。
 */
@Service
public class PayoutServiceImpl implements PayoutService {

    /**
     * service-payout 内部调用客户端。
     */
    private final PayoutInternalClient payoutInternalClient;

    /**
     * OpenAPI 密钥材料工具。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    /**
     * OpenAPI 请求上下文访问器。
     */
    private final OpenApiRequestContext requestContext;

    /**
     * 创建开放接口代付服务实现。
     *
     * @param payoutInternalClient    service-payout 内部调用客户端
     * @param keyMaterialFactory      OpenAPI 密钥材料工具
     * @param requestContext          OpenAPI 请求上下文访问器
     */
    public PayoutServiceImpl(PayoutInternalClient payoutInternalClient,
                             OpenApiKeyMaterialFactory keyMaterialFactory,
                             OpenApiRequestContext requestContext) {
        this.payoutInternalClient = payoutInternalClient;
        this.keyMaterialFactory = keyMaterialFactory;
        this.requestContext = requestContext;
    }

    /**
     * 创建代付交易。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的代付请求参数
     * @return 代付受理标识
     */
    @Override
    public String createPayout(String encryptedData, PayoutCreateRequestDTO requestDTO) {
        PayoutCreateClientRequestDTO clientRequestDTO = toPayoutClientRequest(encryptedData, requestDTO);
        PayoutCreateClientResponseDTO clientResponseDTO = payoutInternalClient.createPayout(clientRequestDTO);
        return clientResponseDTO.getPayoutOrderNo();
    }

    /**
     * 构建调用 service-payout 的内部请求。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的代付请求参数
     * @return 代付内部创建请求
     */
    private PayoutCreateClientRequestDTO toPayoutClientRequest(String encryptedData, PayoutCreateRequestDTO requestDTO) {
        PayoutCreateClientRequestDTO clientRequestDTO = new PayoutCreateClientRequestDTO();
        clientRequestDTO.setMerchantId(requestContext.getRequiredMerchantId());
        clientRequestDTO.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
        clientRequestDTO.setAmount(BigDecimal.valueOf(requestDTO.getAmount()));
        clientRequestDTO.setCurrency(requestDTO.getCurrency());
        clientRequestDTO.setTransactionDateTime(LocalDateTime.now());
        clientRequestDTO.setRequestFingerprint(keyMaterialFactory.fingerprint(encryptedData));
        return clientRequestDTO;
    }
}
