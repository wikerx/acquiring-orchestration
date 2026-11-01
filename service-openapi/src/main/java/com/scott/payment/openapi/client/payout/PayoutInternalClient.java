package com.scott.payment.openapi.client.payout;

import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;


/**
 * OpenAPI 调用代付核心的内部客户端契约；商户入口不直接拥有代付状态。
 */
public interface PayoutInternalClient {

    /**
     * 调用 service-payout 创建代付交易。
     *
     * @param requestDTO 创建代付内部请求
     * @return 创建代付内部响应
     */
    PayoutCreateClientResponseDTO createPayout(PayoutCreateClientRequestDTO requestDTO);
}
