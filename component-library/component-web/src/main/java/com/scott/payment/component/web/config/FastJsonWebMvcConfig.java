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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FastJsonWebMvcConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Fast Json Web Mvc 配置，位于 component-library/component-web 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
public class FastJsonWebMvcConfig implements WebMvcConfigurer {

    /**
     * 替换 Spring MVC 默认 JSON 转换器，统一使用 fastjson2 处理请求和响应报文。
     *
     * @param converters Spring MVC 当前注册的消息转换器列表
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param converters 请求参数或业务处理上下文，不能为空时由上层校验约束。
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

    private FastJsonConfig fastJsonConfig() {
        FastJsonConfig config = new FastJsonConfig();
        config.setReaderFeatures(JSONReader.Feature.FieldBased);
        config.setWriterFeatures(JSONWriter.Feature.WriteMapNullValue);
        return config;
    }
}
