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
 * @description : Exchange Rate Provider Registry 协作组件，位于 调度任务服务，封装 exchange汇率providerregistry 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class ExchangeRateProviderRegistry {

    /**
     * provider Map，用于保存 Exchange Rate Provider Registry 中与 providermap 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
