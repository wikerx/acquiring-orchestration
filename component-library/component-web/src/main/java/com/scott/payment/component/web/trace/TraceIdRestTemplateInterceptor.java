package com.scott.payment.component.web.trace;

import com.scott.payment.component.core.trace.TraceContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceIdRestTemplateInterceptor
 * @date : 2026-07-26 15:20
 * @email : scott_x@163.com
 * @description : RestTemplate traceId 拦截器，在服务间 HTTP 调用时自动透传 X-Trace-Id 请求头。
 * @status : create
 */
public class TraceIdRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    @Override
/**
 * 完成 intercept 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @param body body 输入值，含义由调用方法名称和所属业务对象限定
 * @param execution execution 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(TraceContext.TRACE_ID_HEADER, TraceContext.getOrCreateTraceId());
        return execution.execute(request, body);
    }
}
