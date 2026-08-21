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

@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BocExchangeRateProvider
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Boc Exchange Rate Provider 协作组件，位于 调度任务服务，封装 bocexchange汇率provider 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class BocExchangeRateProvider implements ExchangeRateProvider {

    /**
     * SOURCE CODE，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    public static final String SOURCE_CODE = "BOC";

    /**
     * parser，用于保存 Boc Exchange Rate Provider 中与 parser 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "BOC exchange rate page request failed: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "BOC exchange rate page request interrupted: " + e.getClass().getSimpleName());
        }
    }
}
