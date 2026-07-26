package com.scott.payment.openapi.client.payout;

import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutInternalClient
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : Payout Internal Client 客户端，位于 商户开放接口服务，封装内部服务或渠道接口调用，统一处理请求构造、响应解析、超时和异常转换。
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
