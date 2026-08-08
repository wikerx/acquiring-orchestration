package com.scott.payment.openapi.api.rest.checkout.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.openapi.application.checkout.OpenApiHostedCheckoutApplicationService;
import com.scott.payment.openapi.dto.body.HostedCheckoutBrowserRequestDTOs;
import jakarta.servlet.http.HttpServletRequest;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutPaymentResultVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutCardBinVO;
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

    /**
     * Hosted Checkout 应用编排服务；浏览器入口不直接访问支付核心或持久化会话状态。
     */
    private final OpenApiHostedCheckoutApplicationService hostedCheckoutApplicationService;

    /**
     * 创建付款人浏览器 Hosted Checkout 控制器。
     *
     * @param hostedCheckoutApplicationService Hosted Checkout 应用编排服务
     */
    public HostedCheckoutBrowserController(OpenApiHostedCheckoutApplicationService hostedCheckoutApplicationService) {
        this.hostedCheckoutApplicationService = hostedCheckoutApplicationService;
    }

    /**
     * 使用不透明访问令牌查询收银台会话展示快照。
     *
     * @param requestDTO 会话查询请求，令牌不得写入日志或错误响应
     * @return 当前会话的可公开展示信息
     */
    @PostMapping("/session/query")
    public CommonResult<HostedCheckoutSessionVO> querySession(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.SessionQueryRequest requestDTO) {
        return success(hostedCheckoutApplicationService.querySession(requestDTO));
    }

    /**
     * 提交一次付款尝试。
     *
     * <p>请求中的 PAN 和 CVV 仅允许进入本次支付调用链，控制器不得记录、缓存或回显完整卡数据。</p>
     *
     * @param requestDTO 支付提交请求
     * @return 支付结果、处理中状态或 3DS 后续动作
     */
    @PostMapping("/payment/submit")
    public CommonResult<HostedCheckoutPaymentResultVO> submitPayment(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO) {
        return success(hostedCheckoutApplicationService.submitPayment(requestDTO));
    }

    /**
     * 查询当前 Hosted Checkout 支付尝试状态。
     *
     * @param requestDTO 会话及支付尝试查询条件
     * @return 支付核心返回的当前状态快照
     */
    @PostMapping("/payment/status")
    public CommonResult<HostedCheckoutPaymentResultVO> queryPaymentStatus(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.PaymentStatusRequest requestDTO) {
        return success(hostedCheckoutApplicationService.queryPaymentStatus(requestDTO));
    }

    /** 卡号输入完成后按 BIN 识别品牌并校验商户 MID 支持状态。 */
    @PostMapping("/card-bin/resolve")
    public CommonResult<HostedCheckoutCardBinVO> resolveCardBin(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.CardBinRequest requestDTO) {
        return success(hostedCheckoutApplicationService.resolveCardBin(requestDTO));
    }

    /**
     * 受理付款人完成 3DS 后的回跳数据。
     *
     * <p>一次性回跳令牌和认证载荷只用于推进支付核心状态机，本入口不自行判定交易终态。</p>
     *
     * @param requestDTO 3DS 回跳请求
     * @return 支付核心处理后的当前支付状态
     */
    @PostMapping("/3ds/return")
    public CommonResult<HostedCheckoutPaymentResultVO> handleThreeDsReturn(
            @Valid @RequestBody HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest requestDTO) {
        return success(hostedCheckoutApplicationService.handleThreeDsReturn(requestDTO));
    }

    /**
     * 处理使用 GET 返回的 3DS 浏览器桥接请求。
     *
     * @param request 渠道返回的浏览器请求
     * @return 向父页面传递受控回跳载荷的 HTML
     */
    @GetMapping(value = "/3ds/bridge", produces = MediaType.TEXT_HTML_VALUE)
    public String threeDsBridgeGet(HttpServletRequest request) {
        return threeDsBridgePage(request);
    }

    /**
     * 处理使用 POST 返回的 3DS 浏览器桥接请求。
     *
     * @param request 渠道返回的浏览器请求
     * @return 向父页面传递受控回跳载荷的 HTML
     */
    @PostMapping(value = "/3ds/bridge", produces = MediaType.TEXT_HTML_VALUE)
    public String threeDsBridgePost(HttpServletRequest request) {
        return threeDsBridgePage(request);
    }

    /**
     * 构造 3DS 回跳桥接页。
     *
     * <p>会话号、尝试号和一次性回跳令牌供父页面继续调用受理接口；渠道认证参数仅传递
     * SHA-256 短摘要，避免在页面脚本中暴露原始 3DS 协议载荷。</p>
     *
     * @param request 渠道返回的浏览器请求
     * @return 完成脚本转义的桥接页 HTML
     */
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

    /**
     * 提取渠道 3DS 认证参数的不可逆短摘要。
     *
     * @param request 渠道返回的浏览器请求
     * @return 仅包含已提供参数摘要的有序映射
     */
    private Map<String, String> authenticationData(HttpServletRequest request) {
        Map<String, String> data = new LinkedHashMap<>();
        putMasked(data, "cres", request.getParameter("cres"));
        putMasked(data, "PaRes", request.getParameter("PaRes"));
        putMasked(data, "MD", request.getParameter("MD"));
        putMasked(data, "threeDSSessionData", request.getParameter("threeDSSessionData"));
        putMasked(data, "result", request.getParameter("result"));
        return data;
    }

    /**
     * 在参数非空时写入摘要，原始值不会进入桥接载荷。
     *
     * @param target 摘要目标映射
     * @param key    3DS 参数名称
     * @param value  3DS 参数原始值
     */
    private void putMasked(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, digest16(value));
        }
    }

    /**
     * 转义 JSON 中的 HTML 结束标签片段，防止参数提前闭合脚本元素。
     *
     * @param json 待嵌入脚本的 JSON
     * @return 可安全嵌入当前桥接页脚本字面量的 JSON
     */
    private String escapeScriptJson(String json) {
        return json.replace("</", "<\\/");
    }

    /**
     * 计算 3DS 参数的 SHA-256 短摘要。
     *
     * @param value 原始敏感协议参数
     * @return 16 位十六进制摘要；运行环境缺少算法时返回固定掩码
     */
    private String digest16(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return "***";
        }
    }
}
