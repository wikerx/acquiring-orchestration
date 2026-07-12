package com.scott.payment.job.exchange.provider;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateProviderRegistry
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Exchange Rate Provider Registry，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class ExchangeRateProviderRegistry {

    private final Map<String, ExchangeRateProvider> providerMap = new LinkedHashMap<>();

    /**
     * 注册当前 Spring 容器内的全部汇率源 Provider。
     *
     * @param providers Provider 列表，同一 sourceCode 不允许重复
     */
    public ExchangeRateProviderRegistry(List<ExchangeRateProvider> providers) {
        for (ExchangeRateProvider provider : providers) {
            String sourceCode = provider.sourceCode().toUpperCase(Locale.ROOT);
            if (providerMap.containsKey(sourceCode)) {
                throw new IllegalStateException("duplicated exchange rate provider: " + sourceCode);
            }
            providerMap.put(sourceCode, provider);
        }
    }

    /**
     * 按来源编码获取 Provider。
     *
     * @param sourceCode 汇率源编码
     * @return Provider
     */
    /**
     * 获取汇率管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param sourceCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public ExchangeRateProvider getRequiredProvider(String sourceCode) {
        ExchangeRateProvider provider = providerMap.get(sourceCode.toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "exchange rate provider not found: " + sourceCode);
        }
        return provider;
    }
}
