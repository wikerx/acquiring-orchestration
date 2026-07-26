package com.scott.payment.openapi.api.rest.payout.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.payout.OpenApiPayoutApplicationService;
import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import static com.scott.payment.component.core.model.CommonResult.success;

@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payout/{version}")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayoutController
 * @date : 2026-05-28 10:23
 * @email : scott_x@163.com
 * @description : OpenApiPayoutController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class OpenApiPayoutController {

    /**
     * 开放接口代付业务服务，负责创建代付交易并承接后续状态流转。
     */
    private final OpenApiPayoutApplicationService payoutApplicationService;

    /**
     * 创建开放接口代付控制器。
     *
     * @param payoutApplicationService 开放接口代付应用服务
     */
    public OpenApiPayoutController(OpenApiPayoutApplicationService payoutApplicationService) {
        this.payoutApplicationService = payoutApplicationService;
    }

    /**
     * 创建代付交易。
     *
     * @param request    Servlet 请求上下文
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的代付请求参数
     * @return 代付交易受理结果
     */
    @VerificationAndProcessing(dataReceiver = PayoutCreateRequestDTO.class)
    @PostMapping("/create")
    public CommonResult<String> createPayout(HttpServletRequest request,
                                             @RequestBody String encryptedData,
                                             PayoutCreateRequestDTO requestDTO) {
        return success(payoutApplicationService.createPayout(encryptedData, requestDTO));
    }
}
