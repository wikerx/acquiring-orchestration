package com.scott.payment.data.config;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.Proxy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationConfig
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知 HTTP 配置，提供禁用系统代理且具有有界连接和读取超时的独立客户端
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(DataMerchantNotificationProperties.class)
public class DataMerchantNotificationConfig {

    /**
     * 注册商户通知 HTTP 客户端。
     *
     * @param properties 商户通知执行参数
     * @param traceCustomizer traceId 请求头定制器
     * @return 仅供商户回调使用的 RestTemplate
     */
    @Bean("dataMerchantNotificationRestTemplate")
    public RestTemplate dataMerchantNotificationRestTemplate(DataMerchantNotificationProperties properties,
                                                              TraceIdRestTemplateCustomizer traceCustomizer) {
        validateTimeouts(properties);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        return traceCustomizer.customize(new RestTemplate(requestFactory));
    }

    /**
     * 校验回调超时和任务恢复窗口，防止恢复线程与仍在执行的 HTTP 请求并发发送同一通知。
     *
     * @param properties 商户通知执行参数
     */
    private void validateTimeouts(DataMerchantNotificationProperties properties) {
        if (properties.getConnectTimeoutMillis() <= 0 || properties.getReadTimeoutMillis() <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "merchant notification HTTP timeouts must be greater than zero");
        }
        long minimumProcessingTimeoutMillis = (long) properties.getConnectTimeoutMillis()
                + properties.getReadTimeoutMillis();
        if (properties.getProcessingTimeoutSeconds() <= 0
                || properties.getProcessingTimeoutSeconds() * 1_000L <= minimumProcessingTimeoutMillis) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "merchant notification processing timeout must exceed HTTP timeout");
        }
        if (properties.getEventFallbackBatchLimit() <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "merchant notification event fallback batch limit must be greater than zero");
        }
        if (properties.getRecoveryBatchLimit() <= 0 || properties.getRecoveryBatchLimit() > 500) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "merchant notification recovery batch limit must be between 1 and 500");
        }
    }
}
