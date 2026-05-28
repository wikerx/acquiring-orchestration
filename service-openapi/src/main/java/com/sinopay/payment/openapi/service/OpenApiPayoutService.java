package com.sinopay.payment.openapi.service;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PayoutCreateRequestDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayoutService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口代付服务接口
 * @status : create
 */
public interface OpenApiPayoutService {

    String createPayout(PayoutCreateRequestDTO requestDTO);
}

