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
 * @author : scott
 * @version : v1.0.0
 * @classname : BocExchangeRateProvider
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Boc Exchange Rate Provider，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class BocExchangeRateProvider implements ExchangeRateProvider {

    /**
     * 汇率管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    public static final String SOURCE_CODE = "BOC";

    /**
     * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 执行汇率管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行汇率管理相关处理，保持当前层级的职责边界和返回语义。
     * @param source 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
