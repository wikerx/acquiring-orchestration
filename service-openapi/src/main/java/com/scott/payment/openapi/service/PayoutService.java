package com.scott.payment.openapi.service;

import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;

/**
 * 开放接口代付服务接口
 */
public interface PayoutService {

    /**
     * 创建代付交易。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的代付请求参数
     * @return 代付受理标识
     */
    String createPayout(String encryptedData, PayoutCreateRequestDTO requestDTO);
}
