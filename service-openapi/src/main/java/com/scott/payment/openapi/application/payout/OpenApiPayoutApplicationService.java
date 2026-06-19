package com.scott.payment.openapi.application.payout;

import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.service.PayoutService;
import org.springframework.stereotype.Service;

/**
 * 开放接口代付应用服务。
 * <p>
 * 当前负责衔接接口层与代付业务服务，后续可继续收敛代付预校验、幂等和应用编排逻辑。
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
    public String createPayout(String encryptedData, PayoutCreateRequestDTO requestDTO) {
        return payoutService.createPayout(encryptedData, requestDTO);
    }
}
