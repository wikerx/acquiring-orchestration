package com.scott.payment.job.exchange.provider;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateSourceDO;
import com.scott.payment.job.exchange.parser.NbpExchangeRateJsonParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NbpExchangeRateProvider
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : NBP 汇率源适配器，调用波兰国家银行 Table C API 并输出统一的外币兑 PLN 原始买卖报价。
 * @status : create
 */
@Component
public class NbpExchangeRateProvider implements ExchangeRateProvider {

    /** NBP 汇率源稳定编码，用于 Provider 注册和规则来源匹配。 */
    public static final String SOURCE_CODE = "NBP";
    /**
     * 默认超时秒数常量，统一 {@code NbpExchangeRateProvider} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private final NbpExchangeRateJsonParser parser;

    /**
     * 创建 NBP 汇率源适配器。
     *
     * @param parser NBP Table C JSON 解析器
     */
    public NbpExchangeRateProvider(NbpExchangeRateJsonParser parser) {
        this.parser = parser;
    }

    /**
     * 返回 NBP 汇率源编码。
     *
     * @return 固定返回 NBP
     */
    @Override
    public String sourceCode() {
        return SOURCE_CODE;
    }

    /**
     * 请求 NBP Table C API 并转换为统一原始报价。
     *
     * @param source 汇率源配置，必须包含 requestUrl
     * @return 外币兑 PLN 的即期买入价和即期卖出价
     */
    @Override
    public List<RawRateItem> fetch(ExchangeRateSourceDO source) {
        if (source == null || !StringUtils.hasText(source.getRequestUrl())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "NBP request url is required");
        }
        int timeoutSeconds = source.getTimeoutSeconds() != null && source.getTimeoutSeconds() > 0
                ? source.getTimeoutSeconds()
                : DEFAULT_TIMEOUT_SECONDS;
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(source.getRequestUrl()))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                        "NBP exchange rate API returned status: " + response.statusCode());
            }
            List<RawRateItem> items = parser.parse(response.body());
            if (items.isEmpty()) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                        "NBP exchange rate API returned no valid Table C rates");
            }
            return items;
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "NBP exchange rate API request failed: " + exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "NBP exchange rate API request interrupted: " + exception.getClass().getSimpleName());
        }
    }
}
