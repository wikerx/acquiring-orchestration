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
     * 将 fastjson2 注册为业务 JSON 首选转换器，同时保留 Jackson 处理 Actuator 等框架响应。
     *
     * @param converters Spring MVC 当前注册的消息转换器列表
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(resolveFastJsonConverterIndex(converters), fastJsonHttpMessageConverter());
    }

    /**
     * 确定 fastjson2 转换器在 Spring MVC 转换链中的位置。
     * <p>
     * 转换器优先放在首个 Jackson 转换器之前，确保普通业务 JSON 仍由 fastjson2 处理；
     * 不存在 Jackson 时再放到字符串转换器之后，保留纯文本响应行为。
     * </p>
     *
     * @param converters 当前消息转换器列表
     * @return fastjson2 转换器的插入下标
     */
    private int resolveFastJsonConverterIndex(List<HttpMessageConverter<?>> converters) {
        for (int index = 0; index < converters.size(); index++) {
            if (converters.get(index) instanceof MappingJackson2HttpMessageConverter) {
                return index;
            }
        }
        for (int index = 0; index < converters.size(); index++) {
            if (converters.get(index) instanceof StringHttpMessageConverter) {
                return index + 1;
            }
        }
        return 0;
    }

    /**
     * 创建仅处理 {@code application/json} 的 fastjson2 HTTP 转换器。
     *
     * @return 已应用项目统一读写特性的消息转换器
     */
    private FastJsonHttpMessageConverter fastJsonHttpMessageConverter() {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON));
        converter.setFastJsonConfig(fastJsonConfig());
        return converter;
    }

    /**
     * 创建 Web 层统一 fastjson2 配置。
     *
     * 日期读取使用 fastjson2 的自动识别能力，兼容管理端现有的本地时间和 ISO-8601 请求；
     * 日期写出保留对象实际精度，确保交易分片时间的毫秒不会在 JSON 往返时丢失。
     *
     * @return 启用字段读写、保留 null 字段并自动识别日期格式的转换配置
     */
    private FastJsonConfig fastJsonConfig() {
        FastJsonConfig config = new FastJsonConfig();
        config.setDateFormat(null);
        config.setReaderFeatures(JSONReader.Feature.FieldBased);
        config.setWriterFeatures(JSONWriter.Feature.WriteMapNullValue);
        return config;
    }
}
