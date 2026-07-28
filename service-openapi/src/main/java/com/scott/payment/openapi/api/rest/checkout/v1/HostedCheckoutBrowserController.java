package com.scott.payment.openapi.api.rest.checkout.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.openapi.application.checkout.OpenApiHostedCheckoutApplicationService;
import com.scott.payment.openapi.dto.body.HostedCheckoutBrowserRequestDTOs;
import jakarta.servlet.http.HttpServletRequest;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutPaymentResultVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 付款人浏览器 Hosted Checkout API 控制器。
 */
@RestController
@RequestMapping("/checkout/api/v1")
public class HostedCheckoutBrowserController {

    private final OpenApiHostedCheckoutApplicationService hostedCheckoutApplicationService;

    public HostedCheckoutBrowserController(OpenApiHostedCheckoutApplicationService hostedCheckoutApplicationService) {
        this.hostedCheckoutApplicationService = hostedCheckoutApplicationService;
    }

    @PostMapping("/session/query")
    public CommonResult<HostedCheckoutSessionVO> querySession(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.SessionQueryRequest requestDTO) {
        return success(hostedCheckoutApplicationService.querySession(requestDTO));
    }

    @PostMapping("/payment/submit")
    public CommonResult<HostedCheckoutPaymentResultVO> submitPayment(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO) {
        return success(hostedCheckoutApplicationService.submitPayment(requestDTO));
    }

    @PostMapping("/payment/status")
    public CommonResult<HostedCheckoutPaymentResultVO> queryPaymentStatus(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.PaymentStatusRequest requestDTO) {
        return success(hostedCheckoutApplicationService.queryPaymentStatus(requestDTO));
    }

    @PostMapping("/3ds/return")
    public CommonResult<HostedCheckoutPaymentResultVO> handleThreeDsReturn(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest requestDTO) {
        return success(hostedCheckoutApplicationService.handleThreeDsReturn(requestDTO));
    }

    @GetMapping(value = "/3ds/bridge", produces = MediaType.TEXT_HTML_VALUE)
    public String threeDsBridgeGet(HttpServletRequest request) {
        return threeDsBridgePage(request);
    }

    @PostMapping(value = "/3ds/bridge", produces = MediaType.TEXT_HTML_VALUE)
    public String threeDsBridgePost(HttpServletRequest request) {
        return threeDsBridgePage(request);
    }

    private String threeDsBridgePage(HttpServletRequest request) {
        String checkoutSessionId = request.getParameter("checkoutSessionId");
        String checkoutAttemptId = request.getParameter("checkoutAttemptId");
        String threeDsReturnToken = request.getParameter("threeDsReturnToken");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "HOSTED_CHECKOUT_3DS_RETURN");
        payload.put("checkoutSessionId", checkoutSessionId);
        payload.put("checkoutAttemptId", checkoutAttemptId);
        payload.put("threeDsReturnToken", threeDsReturnToken);
        payload.put("authenticationData", authenticationData(request));
        String payloadJson = escapeScriptJson(JsonUtils.toJsonString(payload));
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>3DS Authentication</title>
                </head>
                <body>
                  <script>
                    (function () {
                      var payload = __PAYLOAD__;
                      if (window.parent && window.parent !== window) {
                        window.parent.postMessage(payload, '*');
                      }
                    }());
                  </script>
                </body>
                </html>
                """.replace("__PAYLOAD__", payloadJson);
    }

    private Map<String, String> authenticationData(HttpServletRequest request) {
        Map<String, String> data = new LinkedHashMap<>();
        putMasked(data, "cres", request.getParameter("cres"));
        putMasked(data, "PaRes", request.getParameter("PaRes"));
        putMasked(data, "MD", request.getParameter("MD"));
        putMasked(data, "threeDSSessionData", request.getParameter("threeDSSessionData"));
        putMasked(data, "result", request.getParameter("result"));
        return data;
    }

    private void putMasked(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, digest16(value));
        }
    }

    private String escapeScriptJson(String json) {
        return json.replace("</", "<\\/");
    }

    private String digest16(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return "***";
        }
    }
}
