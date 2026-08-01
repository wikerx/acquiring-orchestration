package com.scott.payment.openapi.api.rest.checkout.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.checkout.OpenApiHostedCheckoutApplicationService;
import com.scott.payment.openapi.dto.body.HostedCheckoutSessionCreateRequestDTO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionCreateVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 商户 Hosted Checkout OpenAPI V1 控制器。
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/checkout/{version}")
public class OpenApiHostedCheckoutController {

    /**
     * Hosted Checkout 应用编排服务。
     */
    private final OpenApiHostedCheckoutApplicationService hostedCheckoutApplicationService;

    /**
     * 创建商户 Hosted Checkout OpenAPI 控制器。
     *
     * @param hostedCheckoutApplicationService Hosted Checkout 应用编排服务
     */
    public OpenApiHostedCheckoutController(OpenApiHostedCheckoutApplicationService hostedCheckoutApplicationService) {
        this.hostedCheckoutApplicationService = hostedCheckoutApplicationService;
    }

    /**
     * 创建 Hosted Checkout 会话并返回付款人访问地址。
     *
     * <p>{@link VerificationAndProcessing} 在进入方法前完成商户身份校验、防重放、请求解密和参数校验，
     * 并按 OpenAPI 契约处理响应加密。原始密文只用于生成请求指纹，不得写入日志。</p>
     *
     * @param request       当前 HTTP 请求，由安全处理链绑定商户上下文
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密并校验后的会话创建请求
     * @return 会话标识、过期时间及付款人访问地址
     */
    @VerificationAndProcessing(
            dataReceiver = HostedCheckoutSessionCreateRequestDTO.class,
            validationGroups = {
                    HostedCheckoutSessionCreateRequestDTO.Create.class,
                    HostedCheckoutSessionCreateRequestDTO.Format.class
            }
    )
    @PostMapping("/session")
    public CommonResult<HostedCheckoutSessionCreateVO> createSession(HttpServletRequest request,
                                                                     @RequestBody String encryptedData,
                                                                     HostedCheckoutSessionCreateRequestDTO requestDTO) {
        return success(hostedCheckoutApplicationService.createSession(encryptedData, requestDTO));
    }
}
