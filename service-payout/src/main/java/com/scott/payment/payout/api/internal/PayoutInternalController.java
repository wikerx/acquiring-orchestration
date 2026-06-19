package com.scott.payment.payout.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payout.application.PayoutApplicationService;
import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-payout 内部交易接口控制器。
 */
@RestController
@RequestMapping("/internal/payout")
public class PayoutInternalController {

    /**
     * 代付应用服务。
     */
    private final PayoutApplicationService payoutApplicationService;

    /**
     * 创建 service-payout 内部交易接口控制器。
     *
     * @param payoutApplicationService 代付应用服务
     */
    public PayoutInternalController(PayoutApplicationService payoutApplicationService) {
        this.payoutApplicationService = payoutApplicationService;
    }

    /**
     * 创建代付交易。
     *
     * @param commandDTO 创建代付命令
     * @return 代付创建结果
     */
    @PostMapping("/create")
    public CommonResult<PayoutCreateResultDTO> createPayout(@RequestBody PayoutCreateCommandDTO commandDTO) {
        return success(payoutApplicationService.createPayout(commandDTO));
    }
}
