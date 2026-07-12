package com.scott.payment.openapi.client.payout;

import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutInternalClient
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : OpenAPI 调用 service-payout 的内部客户端。
 * @status : create
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
