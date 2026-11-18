package com.scott.payment.admin.api.paymentlink;

import com.alibaba.fastjson2.JSON;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * 只约束支付链接的新建/公开凭据契约；在真实 Fastjson FieldBased 转换前拒绝未开放字段。
 * 不改变旧接口的转换器或把请求正文写入日志。
 */
@ControllerAdvice(assignableTypes = AdminPaymentLinkController.class)
public class PaymentLinkRequestBodyAdvice extends RequestBodyAdviceAdapter {
    /**
     * 仅匹配本功能受限输入，查询与旧业务接口保持既有契约。
     */
    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.Create.class;
    }

    /**
     * 校验字段名后重放相同原始字节，金额精度与 Bean Validation 仍由原 DTO/领域负责。
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
                                          Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] body = input.getBody().readAllBytes();
        Set<String> allowed = Set.of("requestId", "linkName", "merchantOrderNo", "description", "currency",
                "amount", "expireMinutes", "expireTime", "locale", "cardBrands", "notifyUrl", "redirectUrl");
        try {
            var object = JSON.parseObject(body);
            if (object == null || !allowed.containsAll(object.keySet())) {
                throw new IllegalArgumentException("Unsupported payment link field");
            }
        } catch (RuntimeException malformed) {
            // 不带原异常或正文，避免令牌/客户信息进入错误日志和外部响应。
            throw new HttpMessageNotReadableException("Invalid payment link request", input);
        }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
}
