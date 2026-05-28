package com.sinopay.payment.openapi.service.impl;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PayoutCreateRequestDTO;
import com.sinopay.payment.openapi.service.OpenApiPayoutService;
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

    @Override
    public String createPayout(PayoutCreateRequestDTO requestDTO) {
        return requestDTO.getMerchantOrderNo();
    }
}

