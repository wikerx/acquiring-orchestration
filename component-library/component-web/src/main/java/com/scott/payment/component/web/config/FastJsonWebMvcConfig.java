package com.scott.payment.component.web.config;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FastJsonWebMvcConfig
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Web 层统一 fastjson2 报文转换配置
 * @status : create
 */
@Configuration
public class FastJsonWebMvcConfig implements WebMvcConfigurer {

    /**
     * 替换 Spring MVC 默认 JSON 转换器，统一使用 fastjson2 处理请求和响应报文。
     *
     * @param converters Spring MVC 当前注册的消息转换器列表
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
        converters.add(resolveFastJsonConverterIndex(converters), fastJsonHttpMessageConverter());
    }

    private int resolveFastJsonConverterIndex(List<HttpMessageConverter<?>> converters) {
        for (int index = 0; index < converters.size(); index++) {
            if (converters.get(index) instanceof StringHttpMessageConverter) {
                return index + 1;
            }
        }
        return 0;
    }

    private FastJsonHttpMessageConverter fastJsonHttpMessageConverter() {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON));
        converter.setFastJsonConfig(fastJsonConfig());
        return converter;
    }

    /**
     * 完成 fast Json Config 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 FastJsonWebMvcConfig 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private FastJsonConfig fastJsonConfig() {
        FastJsonConfig config = new FastJsonConfig();
        config.setReaderFeatures(JSONReader.Feature.FieldBased);
        config.setWriterFeatures(JSONWriter.Feature.WriteMapNullValue);
        return config;
    }
}
