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

    private final OpenApiHostedCheckoutApplicationService hostedCheckoutApplicationService;

    public OpenApiHostedCheckoutController(OpenApiHostedCheckoutApplicationService hostedCheckoutApplicationService) {
        this.hostedCheckoutApplicationService = hostedCheckoutApplicationService;
    }

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
