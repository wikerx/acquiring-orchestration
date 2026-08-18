package com.scott.payment.component.web.config;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /** 交易分片时间经过业务 JSON 转换器读写后必须保留实际毫秒精度。 */
    @Test
    void shouldPreserveMillisecondPrecisionForLocalDateTime() throws Exception {
        FastJsonHttpMessageConverter converter = fastJsonConverter();

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(
                new DateTimePayload(LocalDateTime.of(2026, 8, 5, 13, 28, 42, 125_000_000)),
                org.springframework.http.MediaType.APPLICATION_JSON,
                output);
        assertThat(output.getBodyAsString()).contains("2026-08-05 13:28:42.125");

        assertThat(readDateTime(converter, "2026-08-05 13:28:42.125"))
                .isEqualTo(LocalDateTime.of(2026, 8, 5, 13, 28, 42, 125_000_000));
    }

    /** 管理端现有请求中的空格时间、ISO 本地时间和 UTC 时间必须能够同时反序列化。 */
    @Test
    void shouldReadSupportedAdminDateTimeFormats() throws Exception {
        FastJsonHttpMessageConverter converter = fastJsonConverter();

        assertThat(readDateTime(converter, "2026-08-05 13:28:42"))
                .isEqualTo(LocalDateTime.of(2026, 8, 5, 13, 28, 42));
        assertThat(readDateTime(converter, "2026-08-05 13:28:42.125"))
                .isEqualTo(LocalDateTime.of(2026, 8, 5, 13, 28, 42, 125_000_000));
        assertThat(readDateTime(converter, "2026-08-05T13:28:42"))
                .isEqualTo(LocalDateTime.of(2026, 8, 5, 13, 28, 42));
        assertThat(readDateTime(converter, "2026-08-04T16:00:00.000Z"))
                .isEqualTo(LocalDateTime.ofInstant(
                        Instant.parse("2026-08-04T16:00:00.000Z"), ZoneId.systemDefault()));
    }

    /** 创建与 Spring MVC 实际注册顺序一致的 fastjson2 转换器。 */
    private FastJsonHttpMessageConverter fastJsonConverter() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new MappingJackson2HttpMessageConverter());
        new FastJsonWebMvcConfig().extendMessageConverters(converters);
        return converters.stream()
                .filter(FastJsonHttpMessageConverter.class::isInstance)
                .map(FastJsonHttpMessageConverter.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /** 通过 HTTP 消息转换器读取单个时间字段，覆盖真实请求反序列化路径。 */
    private LocalDateTime readDateTime(FastJsonHttpMessageConverter converter, String value) throws Exception {
        MockHttpInputMessage input = new MockHttpInputMessage(
                ("{\"transactionDateTime\":\"" + value + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
        input.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        DateTimePayload parsed = (DateTimePayload) converter.read(DateTimePayload.class, input);
        return parsed.getTransactionDateTime();
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

    /** 仅用于验证 Web JSON 时间精度契约的测试载荷。 */
    public static final class DateTimePayload {

        private LocalDateTime transactionDateTime;

        public DateTimePayload() {
        }

        public DateTimePayload(LocalDateTime transactionDateTime) {
            this.transactionDateTime = transactionDateTime;
        }

        public LocalDateTime getTransactionDateTime() {
            return transactionDateTime;
        }

        public void setTransactionDateTime(LocalDateTime transactionDateTime) {
            this.transactionDateTime = transactionDateTime;
        }
    }
}
