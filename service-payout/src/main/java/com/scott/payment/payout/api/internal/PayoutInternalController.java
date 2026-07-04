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
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutInternalController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Payout Internal 管理接口，位于 service-payout 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param commandDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/create")
    public CommonResult<PayoutCreateResultDTO> createPayout(@RequestBody PayoutCreateCommandDTO commandDTO) {
        return success(payoutApplicationService.createPayout(commandDTO));
    }
}
