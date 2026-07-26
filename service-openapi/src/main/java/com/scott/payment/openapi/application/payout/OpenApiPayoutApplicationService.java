package com.scott.payment.openapi.application.payout;

import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.service.PayoutService;
import org.springframework.stereotype.Service;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayoutApplicationService
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : OpenApiPayoutApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
    public String createPayout(String encryptedData, PayoutCreateRequestDTO requestDTO) {
        return payoutService.createPayout(encryptedData, requestDTO);
    }
}
