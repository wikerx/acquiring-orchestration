package com.scott.payment.component.web.config;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FastJsonWebMvcConfigTest
 * @date : 2026-07-31 18:10
 * @email : scott_x@163.com
 * @description : 验证业务 JSON 优先使用 fastjson2，同时为 Actuator 等框架响应保留 Jackson 兜底转换能力
 * @status : create
 */
class FastJsonWebMvcConfigTest {

    /**
     * 扩展转换器列表后必须同时保留 fastjson2 和 Jackson，且业务优先的 fastjson2 排在 Jackson 之前。
     */
    @Test
    void shouldKeepJacksonFallbackAfterRegisteringFastJson() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new StringHttpMessageConverter());
        converters.add(new MappingJackson2HttpMessageConverter());

        new FastJsonWebMvcConfig().extendMessageConverters(converters);

        assertThat(converters).anyMatch(MappingJackson2HttpMessageConverter.class::isInstance);
        int fastJsonIndex = converterIndex(converters, FastJsonHttpMessageConverter.class);
        int jacksonIndex = converterIndex(converters, MappingJackson2HttpMessageConverter.class);
        assertThat(fastJsonIndex).isGreaterThanOrEqualTo(0).isLessThan(jacksonIndex);
    }

    /**
     * 查询指定转换器类型的首个下标，供顺序断言使用。
     *
     * @param converters Spring MVC 转换器列表
     * @param converterType 目标转换器类型
     * @return 首个匹配下标；不存在时返回 -1
     */
    private int converterIndex(List<HttpMessageConverter<?>> converters,
                               Class<? extends HttpMessageConverter> converterType) {
        for (int index = 0; index < converters.size(); index++) {
            if (converterType.isInstance(converters.get(index))) {
                return index;
            }
        }
        return -1;
    }
}
