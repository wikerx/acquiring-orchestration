package com.scott.payment.job.exchange.provider;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateProviderRegistry
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : ExchangeRateProviderRegistry Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class ExchangeRateProviderRegistry {

    /**
     * provider Map 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
