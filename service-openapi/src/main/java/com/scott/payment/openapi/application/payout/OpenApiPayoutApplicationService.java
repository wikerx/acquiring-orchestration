package com.scott.payment.openapi.application.payout;

import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.service.PayoutService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayoutApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Payout Application 服务契约，位于 service-openapi 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class OpenApiPayoutApplicationService {

    /**
     * 开放接口代付业务服务。
     */
    private final PayoutService payoutService;

    /**
     * 创建开放接口代付应用服务。
     *
     * @param payoutService 开放接口代付业务服务
     */
    public OpenApiPayoutApplicationService(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    /**
     * 创建代付交易。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的代付请求参数
     * @return 代付受理标识
     */
    /**
     * 创建或保存商户 OpenAPI数据，保持请求校验、默认值和审计字段一致。
     * @param encryptedData 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public String createPayout(String encryptedData, PayoutCreateRequestDTO requestDTO) {
        return payoutService.createPayout(encryptedData, requestDTO);
    }
}
