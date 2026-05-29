package com.scott.payment.openapi.api.rest.payout.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.service.OpenApiPayoutService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayoutController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口代付控制器
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payout/{version}")
public class OpenApiPayoutController {

    /**
     * 开放接口代付业务服务，负责创建代付交易并承接后续状态流转。
     */
    private final OpenApiPayoutService openApiPayoutService;

    public OpenApiPayoutController(OpenApiPayoutService openApiPayoutService) {
        this.openApiPayoutService = openApiPayoutService;
    }

    /**
     * 创建代付交易。
     *
     * @param request    Servlet 请求上下文
     * @param encydata   商户密文请求体
     * @param requestDTO 解密后的代付请求参数
     * @return 代付交易受理结果
     */
    @VerificationAndProcessing(dataReceiver = PayoutCreateRequestDTO.class)
    @PostMapping("/create")
    public CommonResult<String> createPayout(HttpServletRequest request,
                                             @RequestBody String encydata,
                                             PayoutCreateRequestDTO requestDTO) {
        return CommonResult.success(openApiPayoutService.createPayout(encydata, requestDTO));
    }
}
