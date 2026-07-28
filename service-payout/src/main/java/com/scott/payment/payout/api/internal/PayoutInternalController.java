package com.scott.payment.payout.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payout.application.PayoutApplicationService;
import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/internal/payout")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutInternalController
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : Payout Internal Controller 控制器，位于 代付服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
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
    public CommonResult<PayoutCreateResultDTO> createPayout(@Valid @RequestBody PayoutCreateCommandDTO commandDTO) {
        return success(payoutApplicationService.createPayout(commandDTO));
    }
}
