package com.scott.payment.openapi.client.payout;

import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutInternalClient
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : PayoutInternalClient 内部或渠道客户端，用于封装远程调用、协议参数和异常转换，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
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
