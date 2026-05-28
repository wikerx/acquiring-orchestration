package com.scott.payment.openapi.service.impl;

import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.service.OpenApiPayoutService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayoutServiceImpl
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口代付服务实现
 * @status : create
 */
@Service
public class OpenApiPayoutServiceImpl implements OpenApiPayoutService {

    /**
     * 创建代付交易。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的代付请求参数
     * @return 代付受理标识
     */
    @Override
    public String createPayout(String encryptedData, PayoutCreateRequestDTO requestDTO) {
        return requestDTO.getMerchantOrderNo();
    }
}
