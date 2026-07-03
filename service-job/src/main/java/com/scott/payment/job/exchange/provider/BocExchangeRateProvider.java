package com.scott.payment.job.exchange.provider;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateSourceDO;
import com.scott.payment.job.exchange.parser.BocExchangeRateHtmlParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 中国银行外汇牌价 Provider。
 *
 * <p>负责请求中行页面并交给解析器转换为系统统一原始汇率结构。</p>
 */
@Component
public class BocExchangeRateProvider implements ExchangeRateProvider {

    public static final String SOURCE_CODE = "BOC";

    private final BocExchangeRateHtmlParser parser;

    /**
     * 构造中国银行汇率 Provider。
     *
     * @param parser 中行页面解析器
     */
    public BocExchangeRateProvider(BocExchangeRateHtmlParser parser) {
        this.parser = parser;
    }

    /**
     * 返回中国银行汇率源编码。
     *
     * @return 固定返回 BOC
     */
    @Override
    public String sourceCode() {
        return SOURCE_CODE;
    }

    /**
     * 请求中国银行外汇牌价页面并解析为统一原始报价。
     *
     * @param source 汇率源配置，必须包含 requestUrl 和超时时间
     * @return 原始报价列表
     */
    @Override
    public List<RawRateItem> fetch(ExchangeRateSourceDO source) {
        if (source == null || !StringUtils.hasText(source.getRequestUrl())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "BOC request url is required");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(source.getTimeoutSeconds() == null ? 10 : source.getTimeoutSeconds()))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(source.getRequestUrl()))
                .timeout(Duration.ofSeconds(source.getTimeoutSeconds() == null ? 10 : source.getTimeoutSeconds()))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                        "BOC exchange rate page returned status: " + response.statusCode());
            }
            return parser.parse(response.body());
        } catch (IOException e) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "BOC exchange rate page request failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "BOC exchange rate page request interrupted: " + e.getMessage());
        }
    }
}
