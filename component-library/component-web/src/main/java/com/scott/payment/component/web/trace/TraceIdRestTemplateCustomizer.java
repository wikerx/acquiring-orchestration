package com.scott.payment.component.web.trace;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceIdRestTemplateCustomizer
 * @date : 2026-07-26 15:30
 * @email : scott_x@163.com
 * @description : RestTemplate traceId 定制器，为手工创建的 HTTP 客户端统一追加 X-Trace-Id 传播拦截器。
 * @status : create
 */
public class TraceIdRestTemplateCustomizer {

    /**
     * interceptor 依赖，用于 Trace ID Rest Template Customizer 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TraceIdRestTemplateInterceptor interceptor;

    /**
     * 规范化traceIDresttemplatecustomizer，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param interceptor interceptor 输入值，参与 interceptor 的查询、校验、转换、写入或日志摘要
     */
    public TraceIdRestTemplateCustomizer(TraceIdRestTemplateInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /**
     * 将 traceId 拦截器追加到 RestTemplate。
     *
     * @param restTemplate 待增强的 RestTemplate
     * @return 已追加 traceId 拦截器的 RestTemplate
     */
    public RestTemplate customize(RestTemplate restTemplate) {
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
        interceptors.add(interceptor);
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }
}
