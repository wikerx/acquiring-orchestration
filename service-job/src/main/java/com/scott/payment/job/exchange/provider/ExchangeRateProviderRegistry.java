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
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : 汇率汇率提供方注册表协作组件，位于 调度任务服务，封装该业务的本地校验、转换或运行时协作入口。
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
    public ExchangeRateProvider getRequiredProvider(String sourceCode) {
        ExchangeRateProvider provider = providerMap.get(sourceCode.toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "exchange rate provider not found: " + sourceCode);
        }
        return provider;
    }
}
