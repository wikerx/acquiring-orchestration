package com.scott.payment.job.exchange.provider;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 汇率源 Provider 注册表。
 *
 * <p>按汇率源编码管理插件化 Provider，避免任务编排层写死具体来源实现。</p>
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
