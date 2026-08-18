package com.scott.payment.openapi;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiMpgsLiveFlowTests
 * @date : 2026-07-16 16:20
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 到 MPGS 沙箱的真实联网验收测试，默认关闭；只在本地显式传入 openapi.live.enabled=true 时读取 200045 密钥、加密请求并调用本地 service-openapi。
 * @status : create
 */
@Slf4j
class MerchantOpenApiMpgsLiveFlowTests {

    /**
     * 启用真实 OpenAPI 联网测试的系统属性名称。
     */
    private static final String ENABLED_PROPERTY = "openapi.live.enabled";

    /**
     * 启用预期由内风控拒绝的真实 OpenAPI 验收测试。
     */
    private static final String RISK_BLOCK_ENABLED_PROPERTY = "openapi.live.risk-block.enabled";

    /**
     * 启用 Hosted Checkout 3DS 浏览器验收会话创建的系统属性名称。
     */
    private static final String CHECKOUT_ENABLED_PROPERTY = "openapi.live.checkout.enabled";

    /**
     * 写入一次性收银台 URL 的临时文件属性；必须显式提供且不得指向项目目录。
     */
    private static final String CHECKOUT_URL_FILE_PROPERTY = "openapi.live.checkout-url-file";

    /**
     * 本次浏览器验收期望使用的收银台前端基础地址，默认保持本地开发地址。
     */
    private static final String CHECKOUT_FRONTEND_BASE_URL = System.getProperty(
            "openapi.live.checkout-frontend-base-url", "http://127.0.0.1:5175");

    /**
     * 本地 OpenAPI 基础地址，默认指向 service-openapi 的开发端口。
     */
    private static final String OPENAPI_BASE_URL = System.getProperty("openapi.live.base-url", "http://127.0.0.1:8004");

    /**
     * 本地 MySQL 连接地址，只用于读取 200045 测试商户密钥和核验交易落库结果。
     */
    private static final String JDBC_URL = System.getProperty(
            "openapi.live.jdbc-url",
            "jdbc:mysql://127.0.0.1:3306/payment_acquiring?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    );

    /**
     * 本地 MySQL 用户名。
     */
    private static final String JDBC_USER = System.getProperty("openapi.live.jdbc-user", "root");

    /**
     * 本地 MySQL 密码；外部执行脚本通过环境变量传入，避免凭据出现在 Maven 命令行。
     */
    private static final String JDBC_PASSWORD = propertyOrEnvironment(
            "openapi.live.jdbc-password",
            "OPENAPI_LIVE_JDBC_PASSWORD",
            null
    );

    /**
     * 真实验收使用的测试商户号。
     */
    private static final String MERCHANT_ID = System.getProperty("openapi.live.merchant-id", "200045");

    /**
     * 专项风控验收使用的可信网关客户端 IP；未配置时不发送该请求头。
     */
    private static final String GATEWAY_CLIENT_IP = System.getProperty(
            "openapi.live.gateway-client-ip", "");

    /**
     * 专项来源网址验收使用的 Origin；未配置时不发送该请求头。
     */
    private static final String SOURCE_ORIGIN = System.getProperty(
            "openapi.live.source-origin", "");

    /**
     * MPGS 沙箱测试卡号，只进入加密请求体；日志只允许输出脱敏值。
     */
    private static final String TEST_CARD_NO = System.getProperty("openapi.live.card-no", "5123450000000008");

    /**
     * MPGS 沙箱测试卡品牌，用于按官方测试卡表切换不同卡组织并保持管理端展示准确。
     */
    private static final String TEST_CARD_BRAND = System.getProperty("openapi.live.card-brand", "MASTERCARD");

    /**
     * MPGS 沙箱测试卡有效期月份。
     */
    private static final String TEST_CARD_EXPIRY_MONTH = System.getProperty("openapi.live.card-expiry-month", "01");

    /**
     * MPGS 沙箱测试卡有效期年份。
     */
    private static final String TEST_CARD_EXPIRY_YEAR = System.getProperty("openapi.live.card-expiry-year", "2039");

    /**
     * MPGS 沙箱测试卡安全码，严禁日志输出。
     */
    private static final String TEST_CARD_CVV = System.getProperty("openapi.live.card-cvv", "100");

    /**
     * 单次请求超时时间，避免外部沙箱异常时测试长时间挂起。
     */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(70);

    /**
     * 查询交易结果的最大等待次数。
     */
    private static final int QUERY_RETRY_TIMES = 12;

    /**
     * 按系统属性、环境变量、默认值的顺序读取 Live 测试配置。
     *
     * <p>密码优先通过环境变量传递，避免进入 Maven 参数和进程列表；该方法不得记录解析结果。</p>
     *
     * @param propertyName JVM 系统属性名
     * @param environmentName 环境变量名
     * @param fallback 本地开发默认值
     * @return 首个非空配置值
     */
    private static String propertyOrEnvironment(String propertyName,
                                                String environmentName,
                                                String fallback) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        return fallback;
    }

    /**
     * 查询交易结果的间隔，单位毫秒。
     */
    private static final long QUERY_RETRY_INTERVAL_MILLIS = 500L;

    /**
     * OpenAPI 报文加密工具。
     */
    private final OpenApiPayloadCrypto payloadCrypto = new OpenApiPayloadCrypto();

    /**
     * 密钥摘要工具，只用于日志中输出长度和指纹，不输出真实密钥。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory = new OpenApiKeyMaterialFactory();

    /**
     * JDK HTTP 客户端，用于调用本地 service-openapi。
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * 真实联网验收：覆盖授权请款、支付退款、授权撤销、异常 3DS 支付、增量授权失败后请款等典型成功和失败分支。
     *
     * @throws Exception HTTP 调用、数据库查询或线程等待异常
     */
    @Test
    @EnabledIfSystemProperty(named = ENABLED_PROPERTY, matches = "true")
    void shouldRunMixedOpenApiFlowsAgainstMpgsSandbox() throws Exception {
        MerchantLiveSecurityMaterial material = loadMerchantMaterial();
        PublicKey platformPublicKey = payloadCrypto.readPublicKey(material.platformPublicKeyX509Base64());
        String batchPrefix = "C20LIVE" + DateTimeFormatter.ofPattern("yyMMddHHmmss").format(LocalDateTime.now());
        log.info("OpenAPI真实MPGS验收开始，context: {}", JsonUtils.toJsonString(Map.of(
                "merchantId", MERCHANT_ID,
                "batchPrefix", batchPrefix,
                "baseUrl", OPENAPI_BASE_URL,
                "card", SensitiveDataMaskUtils.maskPan(TEST_CARD_NO),
                "cardBrand", TEST_CARD_BRAND,
                "merchantKey", MerchantOpenApiTestSupport.safeSecretSummary(material.merchantKey(), keyMaterialFactory),
                "platformPublicKey", MerchantOpenApiTestSupport.safeSecretSummary(material.platformPublicKeyX509Base64(), keyMaterialFactory)
        )));

        LiveOperationResult authorization = submitAndWait("/api/rest/payment/v1/authorization",
                cardPlainText(batchPrefix + "A1", batchPrefix + "A1AUTH", "100", "auth before capture", false),
                material,
                platformPublicKey);
        LiveOperationResult capture = submitAndWait("/api/rest/payment/v1/capture",
                followUpPlainText(batchPrefix + "A1", batchPrefix + "A1CAP", "100", "capture authorization", authorization.transactionId(), false),
                material,
                platformPublicKey);

        LiveOperationResult payment = submitAndWait("/api/rest/payment/v1/payment",
                cardPlainText(batchPrefix + "P1", batchPrefix + "P1PAY", "102", "payment before refund", false),
                material,
                platformPublicKey);
        LiveOperationResult refund = submitAndWait("/api/rest/payment/v1/refund",
                followUpPlainText(batchPrefix + "P1", batchPrefix + "P1REF", "22", "refund payment", payment.transactionId(), false),
                material,
                platformPublicKey);

        LiveOperationResult voidAuthorization = submitAndWait("/api/rest/payment/v1/authorization",
                cardPlainText(batchPrefix + "V1", batchPrefix + "V1AUTH", "103", "auth before void", false),
                material,
                platformPublicKey);
        LiveOperationResult voidResult = submitAndWait("/api/rest/payment/v1/void",
                followUpPlainText(batchPrefix + "V1", batchPrefix + "V1VOID", "103", "void authorization", voidAuthorization.transactionId(), false),
                material,
                platformPublicKey);

        LiveOperationResult failedPayment = submitAndWait("/api/rest/payment/v1/payment",
                cardPlainText(batchPrefix + "F1", batchPrefix + "F1PAY", "104", "payment expected channel failure", true),
                material,
                platformPublicKey);

        LiveOperationResult incrementalBaseAuth = submitAndWait("/api/rest/payment/v1/authorization",
                cardPlainText(batchPrefix + "I1", batchPrefix + "I1AUTH", "105", "auth before incremental", false),
                material,
                platformPublicKey);
        LiveOperationResult failedIncremental = submitAndWait("/api/rest/payment/v1/incremental-authorization",
                followUpPlainText(batchPrefix + "I1", batchPrefix + "I1INC", "15", "incremental expected channel failure", incrementalBaseAuth.transactionId(), false),
                material,
                platformPublicKey);
        LiveOperationResult captureAfterIncremental = submitAndWait("/api/rest/payment/v1/capture",
                followUpPlainText(batchPrefix + "I1", batchPrefix + "I1CAP", "105", "capture after incremental failure", incrementalBaseAuth.transactionId(), false),
                material,
                platformPublicKey);

        List<LiveOperationResult> results = List.of(authorization, capture, payment, refund, voidAuthorization,
                voidResult, failedPayment, incrementalBaseAuth, failedIncremental, captureAfterIncremental);
        assertThat(results).hasSize(10);
        assertThat(results.stream().filter(item -> "SUCCESS".equals(item.status())).count()).isGreaterThanOrEqualTo(8);
        assertThat(failedPayment.status()).isEqualTo("FAILED");
        assertThat(failedIncremental.status()).isEqualTo("FAILED");
        log.info("OpenAPI真实MPGS验收完成，result: {}", JsonUtils.toJsonString(Map.of(
                "batchPrefix", batchPrefix,
                "operations", results
        )));
    }

    /**
     * 真实联网验收：覆盖预授权、预授权完成和交易查询入口，确认它们都能通过网关、OpenAPI 安全链路、支付核心和 MPGS 沙箱。
     *
     * @throws Exception HTTP 调用、数据库查询或响应解密异常
     */
    @Test
    @EnabledIfSystemProperty(named = ENABLED_PROPERTY, matches = "true")
    void shouldRunPreAuthorizationCompletionAndQueryAgainstMpgsSandbox() throws Exception {
        MerchantLiveSecurityMaterial material = loadMerchantMaterial();
        PublicKey platformPublicKey = payloadCrypto.readPublicKey(material.platformPublicKeyX509Base64());
        PrivateKey merchantResponsePrivateKey = payloadCrypto.readPrivateKey(material.merchantResponsePrivateKeyPkcs8Base64());
        String batchPrefix = "C20PRE" + DateTimeFormatter.ofPattern("yyMMddHHmmss").format(LocalDateTime.now());

        LiveOperationResult preAuthorization = submitAndWait("/api/rest/payment/v1/pre-authorization",
                cardPlainText(batchPrefix + "A1", batchPrefix + "A1PRE", "31", "pre authorization before completion", false),
                material,
                platformPublicKey);
        LiveOperationResult completion = submitAndWait("/api/rest/payment/v1/pre-auth-completion",
                followUpPlainText(batchPrefix + "A1", batchPrefix + "A1COMP", "31", "pre authorization completion",
                        preAuthorization.transactionId(), false),
                material,
                platformPublicKey);
        OpenApiLiveResponse queryResponse = submitOnly("/api/rest/payment/v1/query",
                followUpPlainText(batchPrefix + "A1", batchPrefix + "A1QUERY", "31", "query pre authorization",
                        preAuthorization.transactionId(), false),
                material,
                platformPublicKey);
        String queryPlainResponse = payloadCrypto.decrypt(queryResponse.encryptedData(), merchantResponsePrivateKey);
        assertThat(queryPlainResponse).contains(preAuthorization.transactionId(), completion.transactionId(), batchPrefix + "A1");

        log.info("OpenAPI预授权与查询MPGS验收完成，result: {}", JsonUtils.toJsonString(Map.of(
                "batchPrefix", batchPrefix,
                "preAuthorization", preAuthorization,
                "completion", completion,
                "queryResponse", queryResponse.safeSummary(),
                "queryPlainResponseMasked", SensitiveDataMaskUtils.maskJson(queryPlainResponse)
        )));
    }

    /**
     * 真实风控验收：支付、授权和预授权必须返回商户可识别的风控失败，并保留失败交易。
     *
     * @throws Exception HTTP 调用、响应解密、数据库查询或线程等待异常
     */
    @Test
    @EnabledIfSystemProperty(named = RISK_BLOCK_ENABLED_PROPERTY, matches = "true")
    void shouldPersistFailedTransactionsWhenRiskBlocksControlledApis() throws Exception {
        MerchantLiveSecurityMaterial material = loadMerchantMaterial();
        PublicKey platformPublicKey = payloadCrypto.readPublicKey(material.platformPublicKeyX509Base64());
        PrivateKey responsePrivateKey = payloadCrypto.readPrivateKey(
                material.merchantResponsePrivateKeyPkcs8Base64());
        String batchPrefix = "C20RISK" + DateTimeFormatter.ofPattern("yyMMddHHmmss").format(LocalDateTime.now());
        List<RiskBlockedApiCase> apiCases = List.of(
                new RiskBlockedApiCase("/api/rest/payment/v1/payment", "PAY", "PAYMENT"),
                new RiskBlockedApiCase("/api/rest/payment/v1/authorization", "AUT", "AUTHORIZATION"),
                new RiskBlockedApiCase("/api/rest/payment/v1/pre-authorization", "PRE", "PRE_AUTHORIZATION")
        );

        List<LiveOperationResult> operations = new java.util.ArrayList<>();
        for (RiskBlockedApiCase apiCase : apiCases) {
            String orderNo = batchPrefix + apiCase.orderSuffix();
            OpenApiLiveResponse response = submitOnly(
                    apiCase.path(),
                    cardPlainText(orderNo, orderNo + "REQ", "10", "risk blocked " + apiCase.transactionType(), false),
                    material,
                    platformPublicKey
            );
            String plainResponse = payloadCrypto.decrypt(response.encryptedData(), responsePrivateKey);
            Map<String, Object> responsePayload = JsonUtils.parseObject(plainResponse, new TypeReference<>() {
            });
            @SuppressWarnings("unchecked")
            Map<String, Object> transactionInfo = (Map<String, Object>) responsePayload.get("transactionInfo");

            assertThat(transactionInfo).as(apiCase.transactionType() + " transactionInfo").isNotNull();
            assertThat(transactionInfo.get("code")).isEqualTo("F210");
            assertThat(transactionInfo.get("message")).isEqualTo("Risk blocked");
            assertThat(transactionInfo.get("transactionType")).isEqualTo(apiCase.transactionType());

            LiveOperationResult operation = waitOperation(response.identity());
            assertThat(operation.status()).isEqualTo("FAILED");
            assertThat(operation.channelCode()).isNull();
            assertThat(operation.channelTransactionId()).isNull();
            operations.add(operation);
        }

        log.info("OpenAPI内风控拒绝验收完成，result: {}", JsonUtils.toJsonString(Map.of(
                "batchPrefix", batchPrefix,
                "operations", operations
        )));
    }

    /**
     * 按 MPGS 官方基础测试卡表批量验证当前系统已配置的卡品牌。
     * <p>
     * 当前本地渠道能力启用了 Mastercard、Visa、JCB 和 Diners Club；OpenAPI 校验尚未允许
     * DINERS_CLUB 枚举，因此这里先覆盖可从商户接口正确表达且能落库展示的 Mastercard、Visa、JCB。
     *
     * @throws Exception HTTP 调用、数据库查询或线程等待异常
     */
    @Test
    @EnabledIfSystemProperty(named = ENABLED_PROPERTY, matches = "true")
    void shouldRunConfiguredOfficialTestCardsAgainstMpgsSandbox() throws Exception {
        MerchantLiveSecurityMaterial material = loadMerchantMaterial();
        PublicKey platformPublicKey = payloadCrypto.readPublicKey(material.platformPublicKeyX509Base64());
        String batchPrefix = "C20CARD" + DateTimeFormatter.ofPattern("yyMMddHHmmss").format(LocalDateTime.now());
        List<OfficialTestCard> cards = List.of(
                new OfficialTestCard("MASTERCARD", "5123450000000008", "Y"),
                new OfficialTestCard("MASTERCARD", "2223000000000007", "Y"),
                new OfficialTestCard("MASTERCARD", "5111111111111118", "N"),
                new OfficialTestCard("MASTERCARD", "2223000000000023", "N"),
                new OfficialTestCard("VISA", "4508750015741019", "Y"),
                new OfficialTestCard("VISA", "4012000033330026", "N"),
                new OfficialTestCard("JCB", "3528000000000007", "Y"),
                new OfficialTestCard("JCB", "3528111100000001", "N")
        );
        List<LiveOperationResult> results = new java.util.ArrayList<>();
        int index = 1;
        for (OfficialTestCard card : cards) {
            String suffix = card.cardBrand() + index;
            String orderNo = batchPrefix + suffix;
            results.add(submitAndWait("/api/rest/payment/v1/payment",
                    cardPlainText(orderNo, orderNo + "PAY", "11." + index,
                            "official card payment " + card.cardBrand(), false, card),
                    material,
                    platformPublicKey));
            results.add(submitAndWait("/api/rest/payment/v1/authorization",
                    cardPlainText(orderNo, orderNo + "AUTH", "12." + index,
                            "official card authorization " + card.cardBrand(), false, card),
                    material,
                    platformPublicKey));
            index++;
        }
        log.info("OpenAPI官方测试卡MPGS验收完成，result: {}", JsonUtils.toJsonString(Map.of(
                "batchPrefix", batchPrefix,
                "cards", cards,
                "operations", results
        )));
        assertThat(results).hasSize(cards.size() * 2);
    }

    /**
     * 按当前 MPGS 银行卡渠道能力验证多币种真实交易。
     * <p>
     * 当前本地 MPGS BANK_CARD 能力配置支持 CNY、EUR、GBP、HKD、JPY、USD。这里使用官方 Mastercard 测试卡逐币种跑支付和授权，
     * 用于验证标签币种、交易币种、金额精度和 MPGS 沙箱响应是否能正确落库展示。
     *
     * @throws Exception HTTP 调用、数据库查询或线程等待异常
     */
    @Test
    @EnabledIfSystemProperty(named = ENABLED_PROPERTY, matches = "true")
    void shouldRunSupportedCurrenciesAgainstMpgsSandbox() throws Exception {
        MerchantLiveSecurityMaterial material = loadMerchantMaterial();
        PublicKey platformPublicKey = payloadCrypto.readPublicKey(material.platformPublicKeyX509Base64());
        String batchPrefix = "C20CUR" + DateTimeFormatter.ofPattern("yyMMddHHmmss").format(LocalDateTime.now());
        OfficialTestCard card = new OfficialTestCard("MASTERCARD", "5123450000000008", "Y");
        List<CurrencyCase> currencies = List.of(
                new CurrencyCase("USD", "13.01", "14.01"),
                new CurrencyCase("EUR", "13.02", "14.02"),
                new CurrencyCase("GBP", "13.03", "14.03"),
                new CurrencyCase("HKD", "13.04", "14.04"),
                new CurrencyCase("CNY", "13.05", "14.05"),
                new CurrencyCase("JPY", "1306", "1406")
        );
        List<LiveOperationResult> results = new java.util.ArrayList<>();
        int index = 1;
        for (CurrencyCase currencyCase : currencies) {
            String orderNo = batchPrefix + currencyCase.currency() + index;
            results.add(submitAndWait("/api/rest/payment/v1/payment",
                    cardPlainText(orderNo, orderNo + "PAY", currencyCase.paymentAmount(),
                            currencyCase.currency(), "currency payment " + currencyCase.currency(), false, card),
                    material,
                    platformPublicKey));
            results.add(submitAndWait("/api/rest/payment/v1/authorization",
                    cardPlainText(orderNo, orderNo + "AUTH", currencyCase.authorizationAmount(),
                            currencyCase.currency(), "currency authorization " + currencyCase.currency(), false, card),
                    material,
                    platformPublicKey));
            index++;
        }
        log.info("OpenAPI多币种MPGS验收完成，result: {}", JsonUtils.toJsonString(Map.of(
                "batchPrefix", batchPrefix,
                "currencies", currencies,
                "operations", results
        )));
        assertThat(results).hasSize(currencies.size() * 2);
    }

    /**
     * 为真实浏览器验收创建 29.99 USD 非 3DS 与 30.00 USD 强制 3DS 两个收银台会话。
     *
     * <p>该用例只创建会话，不提交卡数据或渠道交易。完整收银台 URL 仅写入显式指定的
     * 仓库外临时文件，文件权限收敛为当前用户读写，日志只输出金额、会话数和文件名。</p>
     *
     * @throws Exception OpenAPI 调用、响应解密或临时文件写入异常
     */
    @Test
    @EnabledIfSystemProperty(named = CHECKOUT_ENABLED_PROPERTY, matches = "true")
    void shouldCreateHostedCheckoutSessionsForThreeDsBoundaryBrowserValidation() throws Exception {
        Path outputFile = checkoutUrlOutputFile();
        MerchantLiveSecurityMaterial material = loadMerchantMaterial();
        PublicKey platformPublicKey = payloadCrypto.readPublicKey(material.platformPublicKeyX509Base64());
        PrivateKey merchantResponsePrivateKey = payloadCrypto.readPrivateKey(
                material.merchantResponsePrivateKeyPkcs8Base64());
        String batchPrefix = "C20HC" + DateTimeFormatter.ofPattern("yyMMddHHmmss").format(LocalDateTime.now());

        List<Map<String, Object>> sessions = new java.util.ArrayList<>();
        for (String amount : List.of("29.99", "30.00")) {
            String orderNo = batchPrefix + amount.replace(".", "");
            OpenApiLiveResponse response = submitOnly(
                    "/api/rest/checkout/v1/session",
                    hostedCheckoutPlainText(orderNo, amount),
                    material,
                    platformPublicKey
            );
            assertThat(response.code()).isEqualTo("T200");
            String plainResponse = payloadCrypto.decrypt(response.encryptedData(), merchantResponsePrivateKey);
            Map<String, Object> payload = JsonUtils.parseObject(plainResponse, new TypeReference<>() {
            });
            @SuppressWarnings("unchecked")
            Map<String, Object> checkoutInfo = (Map<String, Object>) payload.get("checkoutInfo");
            assertThat(checkoutInfo).as(amount + " checkoutInfo").isNotNull();
            String checkoutUrl = Objects.toString(checkoutInfo.get("checkoutUrl"), "");
            assertThat(checkoutUrl).as(amount + " checkoutUrl")
                    .startsWith(CHECKOUT_FRONTEND_BASE_URL + "/");
            sessions.add(Map.of(
                    "amount", amount,
                    "orderNo", orderNo,
                    "checkoutUrl", checkoutUrl
            ));
        }

        Files.writeString(outputFile, JsonUtils.toJsonString(sessions),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.setPosixFilePermissions(outputFile, Set.of(OWNER_READ, OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            outputFile.toFile().setReadable(false, false);
            outputFile.toFile().setWritable(false, false);
            outputFile.toFile().setReadable(true, true);
            outputFile.toFile().setWritable(true, true);
        }
        log.info("Hosted Checkout 3DS边界验收会话已创建，summary: {}", JsonUtils.toJsonString(Map.of(
                "amounts", List.of("29.99", "30.00"),
                "sessionCount", sessions.size(),
                "outputFileName", outputFile.getFileName().toString()
        )));
    }

    /**
     * 提交一笔商户 OpenAPI 交易并等待交易动作落库。
     *
     * @param path              OpenAPI 路径
     * @param plainRequestJson  商户业务明文 JSON，仅在加密前内存使用
     * @param material          商户密钥材料
     * @param platformPublicKey 平台请求体公钥
     * @return 落库后的交易结果摘要
     * @throws IOException HTTP 调用异常
     * @throws InterruptedException HTTP 等待或轮询等待异常
     * @throws SQLException 数据库查询异常
     */
    private LiveOperationResult submitAndWait(String path,
                                              String plainRequestJson,
                                              MerchantLiveSecurityMaterial material,
                                              PublicKey platformPublicKey)
            throws IOException, InterruptedException, SQLException {
        OpenApiLiveResponse response = submitOnly(path, plainRequestJson, material, platformPublicKey);
        assertThat(response.encryptedData()).as(response.identity().orderId() + " encrypted response data").isNotBlank();
        return waitOperation(response.identity());
    }

    /**
     * 提交一笔商户 OpenAPI 请求并返回加密响应，不等待动作单落库。
     *
     * @param path              OpenAPI 路径
     * @param plainRequestJson  商户业务明文 JSON，仅在加密前内存使用
     * @param material          商户密钥材料
     * @param platformPublicKey 平台请求体公钥
     * @return OpenAPI 响应摘要
     * @throws IOException HTTP 调用异常
     * @throws InterruptedException HTTP 等待异常
     */
    private OpenApiLiveResponse submitOnly(String path,
                                           String plainRequestJson,
                                           MerchantLiveSecurityMaterial material,
                                           PublicKey platformPublicKey)
            throws IOException, InterruptedException {
        RequestIdentity identity = requestIdentity(plainRequestJson);
        String encryptedData = payloadCrypto.encrypt(plainRequestJson, platformPublicKey);
        String authorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                material.merchantKey(),
                System.currentTimeMillis() / 1000L,
                MerchantOpenApiTestSupport.uniqueJwtId(identity.orderId())
        );
        String requestBody = MerchantOpenApiTestSupport.wrapEncryptedData(encryptedData);
        log.info("OpenAPI真实请求开始，request: {}", JsonUtils.toJsonString(Map.of(
                "path", path,
                "merchantId", MERCHANT_ID,
                "orderNo", identity.orderNo(),
                "orderId", identity.orderId(),
                "plainMasked", SensitiveDataMaskUtils.maskJson(plainRequestJson),
                "jwt", MerchantOpenApiTestSupport.safeSecretSummary(authorization, keyMaterialFactory),
                "data", MerchantOpenApiTestSupport.safeSecretSummary(encryptedData, keyMaterialFactory)
        )));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(OPENAPI_BASE_URL + path))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .header(MerchantOpenApiTestSupport.AUTHORIZATION_HEADER, authorization);
        if (!GATEWAY_CLIENT_IP.isBlank()) {
            requestBuilder.header("X-Gateway-Client-Ip", GATEWAY_CLIENT_IP.trim());
        }
        if (!SOURCE_ORIGIN.isBlank()) {
            requestBuilder.header("Origin", SOURCE_ORIGIN.trim());
        }
        HttpResponse<String> httpResponse = httpClient.send(
                requestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        CommonResult<String> response = JsonUtils.parseObject(httpResponse.body(), new TypeReference<>() {
        });
        log.info("OpenAPI真实请求返回，response: {}", JsonUtils.toJsonString(Map.of(
                "path", path,
                "orderNo", identity.orderNo(),
                "orderId", identity.orderId(),
                "httpStatus", httpResponse.statusCode(),
                "code", response == null ? null : response.getCode(),
                "message", response == null ? null : response.getMessage(),
                "encryptedData", response == null || response.getData() == null
                        ? Map.of("length", 0)
                        : MerchantOpenApiTestSupport.safeSecretSummary(response.getData(), keyMaterialFactory)
        )));
        assertThat(httpResponse.statusCode()).as(identity.orderId()).isEqualTo(200);
        assertThat(response).as(identity.orderId()).isNotNull();
        assertThat(response.getData()).as(identity.orderId() + " encrypted response data").isNotBlank();
        return new OpenApiLiveResponse(identity, httpResponse.statusCode(), response.getCode(),
                response.getMessage(), response.getData());
    }

    /**
     * 按商户请求唯一标识等待交易动作落库。
     *
     * @param identity 商户订单标识
     * @return 交易动作摘要
     * @throws SQLException 数据库查询异常
     * @throws InterruptedException 等待异常
     */
    private LiveOperationResult waitOperation(RequestIdentity identity) throws SQLException, InterruptedException {
        for (int retry = 0; retry < QUERY_RETRY_TIMES; retry++) {
            Optional<LiveOperationResult> operation = findOperation(identity);
            if (operation.isPresent()) {
                LiveOperationResult result = operation.get();
                log.info("OpenAPI真实交易落库完成，operation: {}", JsonUtils.toJsonString(result));
                return result;
            }
            Thread.sleep(QUERY_RETRY_INTERVAL_MILLIS);
        }
        throw new AssertionError("交易动作未落库：" + identity);
    }

    /**
     * 查询当前商户请求对应的交易动作。
     *
     * @param identity 商户订单标识
     * @return 交易动作摘要
     * @throws SQLException 数据库查询异常
     */
    private Optional<LiveOperationResult> findOperation(RequestIdentity identity) throws SQLException {
        String sql = """
                SELECT transaction_id, source_transaction_id, merchant_order_no,
                       COALESCE(merchant_order_id, merchant_operation_no) AS merchant_order_id,
                       transaction_type, transaction_status, label_amount, label_currency,
                       transaction_amount, transaction_currency, transaction_rate,
                       channel_code, channel_order_no, channel_transaction_id,
                       channel_response_code, channel_response_message, auth_code,
                       transaction_date_time
                 FROM transaction_operation_202603
                 WHERE merchant_id = ?
                   AND merchant_order_no = ?
                   AND (
                        merchant_order_id = ?
                        OR merchant_operation_no = ?
                        OR merchant_operation_no = ?
                   )
                 ORDER BY id DESC
                 LIMIT 1
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MERCHANT_ID);
            statement.setString(2, identity.orderNo());
            statement.setString(3, identity.orderId());
            statement.setString(4, identity.orderId());
            statement.setString(5, identity.orderNo());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LiveOperationResult(
                        rs.getString("merchant_order_no"),
                        rs.getString("merchant_order_id"),
                        rs.getString("transaction_id"),
                        rs.getString("source_transaction_id"),
                        rs.getString("transaction_type"),
                        rs.getString("transaction_status"),
                        rs.getBigDecimal("label_amount"),
                        rs.getString("label_currency"),
                        rs.getBigDecimal("transaction_amount"),
                        rs.getString("transaction_currency"),
                        rs.getBigDecimal("transaction_rate"),
                        rs.getString("channel_code"),
                        rs.getString("channel_order_no"),
                        rs.getString("channel_transaction_id"),
                        rs.getString("channel_response_code"),
                        rs.getString("channel_response_message"),
                        rs.getString("auth_code"),
                        rs.getString("transaction_date_time")
                ));
            }
        }
    }

    /**
     * 读取真实验收商户的 OpenAPI 密钥材料。
     *
     * @return 商户密钥材料
     * @throws SQLException 数据库查询异常
     */
    private MerchantLiveSecurityMaterial loadMerchantMaterial() throws SQLException {
        String sql = """
                SELECT jwt.merchant_key, payload.public_key_x509_base64, response.private_key_pkcs8_base64
                  FROM base_merchant_jwt_key jwt
                  JOIN base_platform_payload_key payload ON payload.merchant_id = jwt.merchant_id
                  JOIN base_merchant_response_key response ON response.merchant_id = jwt.merchant_id
                 WHERE jwt.merchant_id = ?
                   AND jwt.enabled = 1
                   AND jwt.deleted = 0
                   AND payload.enabled = 1
                   AND payload.deleted = 0
                   AND response.enabled = 1
                   AND response.deleted = 0
                 ORDER BY jwt.id DESC
                 LIMIT 1
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MERCHANT_ID);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("merchant openapi material").isTrue();
                return new MerchantLiveSecurityMaterial(
                        rs.getString("merchant_key"),
                        rs.getString("public_key_x509_base64"),
                        rs.getString("private_key_pkcs8_base64")
                );
            }
        }
    }

    /**
     * 创建数据库连接。
     *
     * @return JDBC 连接
     * @throws SQLException 数据库连接异常
     */
    private Connection openConnection() throws SQLException {
        assertThat(JDBC_PASSWORD).as("OpenAPI live-test JDBC password").isNotBlank();
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
    }

    /**
     * 从请求 JSON 中提取商户订单号和本次请求唯一标识。
     *
     * @param plainRequestJson 明文业务 JSON
     * @return 商户订单标识
     */
    @SuppressWarnings("unchecked")
    private RequestIdentity requestIdentity(String plainRequestJson) {
        Map<String, Object> payload = JsonUtils.parseObject(plainRequestJson, new TypeReference<>() {
        });
        Map<String, Object> orderInfo = (Map<String, Object>) payload.get("orderInfo");
        return new RequestIdentity(Objects.toString(orderInfo.get("orderNo")), Objects.toString(orderInfo.get("orderId")));
    }

    /**
     * 构造首次类卡交易请求明文。
     *
     * @param orderNo 商户订单号
     * @param orderId 商户本次请求唯一标识
     * @param amount 金额，主币种单位
     * @param description 交易描述
     * @param includeInvalidThreeDs 是否携带当前 MPGS 沙箱会拒绝的 3DS 字段
     * @return 请求明文 JSON
     */
    private String cardPlainText(String orderNo,
                                 String orderId,
                                 String amount,
                                 String description,
                                 boolean includeInvalidThreeDs) {
        return cardPlainText(orderNo, orderId, amount, "USD", description, includeInvalidThreeDs,
                new OfficialTestCard(TEST_CARD_BRAND, TEST_CARD_NO, "Y"));
    }

    /**
     * 构造指定官方测试卡的首次类卡交易请求明文。
     *
     * @param orderNo 商户订单号
     * @param orderId 商户本次请求唯一标识
     * @param amount 金额，主币种单位
     * @param description 交易描述
     * @param includeInvalidThreeDs 是否携带当前 MPGS 沙箱会拒绝的 3DS 字段
     * @param card 官方测试卡配置
     * @return 请求明文 JSON
     */
    private String cardPlainText(String orderNo,
                                 String orderId,
                                 String amount,
                                 String description,
                                 boolean includeInvalidThreeDs,
                                 OfficialTestCard card) {
        return cardPlainText(orderNo, orderId, amount, "USD", description, includeInvalidThreeDs, card);
    }

    /**
     * 构造指定官方测试卡和币种的首次类卡交易请求明文。
     *
     * @param orderNo 商户订单号
     * @param orderId 商户本次请求唯一标识
     * @param amount 金额，主币种单位
     * @param currency ISO 4217 币种
     * @param description 交易描述
     * @param includeInvalidThreeDs 是否携带当前 MPGS 沙箱会拒绝的 3DS 字段
     * @param card 官方测试卡配置
     * @return 请求明文 JSON
     */
    private String cardPlainText(String orderNo,
                                 String orderId,
                                 String amount,
                                 String currency,
                                 String description,
                                 boolean includeInvalidThreeDs,
                                 OfficialTestCard card) {
        Map<String, Object> payload = basePayload(orderNo, orderId, amount, currency, description, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> transactionInfo = (Map<String, Object>) payload.get("transactionInfo");
        transactionInfo.put("merchantWebsite", "https://merchant.example.com/checkout");
        Map<String, Object> cardInfo = new LinkedHashMap<>();
        cardInfo.put("cardNo", card.cardNo());
        cardInfo.put("expirationMonth", TEST_CARD_EXPIRY_MONTH);
        cardInfo.put("expirationYear", TEST_CARD_EXPIRY_YEAR);
        cardInfo.put("securityCode", TEST_CARD_CVV);
        payload.put("cardInfo", cardInfo);
        payload.put("billingCardHolderInfo", billingCardHolderInfo());
        if (includeInvalidThreeDs) {
            payload.put("threeDSInfo", Map.of(
                    "eci", "212",
                    "cavv", "AAABBIIFmAAAAAAAAAAAAAAAAAA=",
                    "dsTransactionId", "b96c957d-daa1-4b7f-b8b4-373fb9dec47b",
                    "threeDsVersion", "2.2.0"
            ));
        }
        return JsonUtils.toJsonString(payload);
    }

    /**
     * 构造后续动作请求明文。
     *
     * @param orderNo 商户订单号
     * @param orderId 商户本次请求唯一标识
     * @param amount 金额，主币种单位
     * @param description 交易描述
     * @param sourceTransactionId 原平台交易 ID
     * @param includeInvalidThreeDs 是否携带异常 3DS 字段
     * @return 请求明文 JSON
     */
    private String followUpPlainText(String orderNo,
                                     String orderId,
                                     String amount,
                                     String description,
                                     String sourceTransactionId,
                                     boolean includeInvalidThreeDs) {
        Map<String, Object> payload = basePayload(orderNo, orderId, amount, "USD", description, sourceTransactionId);
        if (includeInvalidThreeDs) {
            payload.put("threeDSInfo", Map.of(
                    "eci", "212",
                    "cavv", "AAABBIIFmAAAAAAAAAAAAAAAAAA=",
                    "dsTransactionId", "b96c957d-daa1-4b7f-b8b4-373fb9dec47b",
                    "threeDsVersion", "2.2.0"
            ));
        }
        return JsonUtils.toJsonString(payload);
    }

    /**
     * 构造 OpenAPI 收单交易公共请求体。
     *
     * @param orderNo 商户订单号
     * @param orderId 商户本次请求唯一标识
     * @param amount 交易金额
     * @param description 交易描述
     * @param sourceTransactionId 原平台交易 ID，首次类交易为空
     * @return 请求体 Map
     */
    private Map<String, Object> basePayload(String orderNo,
                                            String orderId,
                                            String amount,
                                            String currency,
                                            String description,
                                            String sourceTransactionId) {
        Map<String, Object> transactionInfo = new LinkedHashMap<>();
        transactionInfo.put("description", description);
        transactionInfo.put("callbackUrl", "https://merchant.example.com/opgs/callback");
        if (sourceTransactionId != null) {
            transactionInfo.put("sourceTransactionId", sourceTransactionId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantInfo", merchantInfo());
        payload.put("orderInfo", Map.of(
                "orderNo", orderNo,
                "orderId", orderId,
                "amount", new BigDecimal(amount),
                "currency", currency
        ));
        payload.put("transactionInfo", transactionInfo);
        return payload;
    }

    /** 构造 MPGS Hosted Checkout 浏览器验收会话请求，不包含任何卡数据。 */
    private String hostedCheckoutPlainText(String orderNo, String amount) {
        Map<String, Object> paymentMethod = new LinkedHashMap<>();
        paymentMethod.put("paymentMethod", "BANK_CARD");
        paymentMethod.put("channelCode", "MPGS");
        paymentMethod.put("brands", List.of("VISA", "MASTERCARD", "JCB"));
        paymentMethod.put("threeDsMode", "AUTO");

        Map<String, Object> checkoutInfo = new LinkedHashMap<>();
        checkoutInfo.put("locale", "zh-CN");
        checkoutInfo.put("expireMinutes", 60);
        checkoutInfo.put("allowedPaymentMethods", List.of(paymentMethod));
        checkoutInfo.put("retryAllowed", true);
        checkoutInfo.put("returnUrl", "https://merchant.example.com/checkout/return");
        checkoutInfo.put("cancelUrl", "https://merchant.example.com/checkout/cancel");
        checkoutInfo.put("notifyUrl", "https://merchant.example.com/opgs/callback");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantInfo", merchantInfo());
        payload.put("orderInfo", Map.of(
                "orderNo", orderNo,
                "orderId", orderNo + "REQ",
                "amount", new BigDecimal(amount),
                "currency", "USD",
                "subject", "MPGS 3DS boundary validation",
                "description", "hosted checkout browser validation"
        ));
        payload.put("checkoutInfo", checkoutInfo);
        payload.put("payerInfo", Map.of(
                "payerId", "CODEX-3DS-TEST",
                "email", "codex@example.com",
                "country", "USA"
        ));
        return JsonUtils.toJsonString(payload);
    }

    /** 校验一次性收银台 URL 输出文件必须位于系统临时目录。 */
    private Path checkoutUrlOutputFile() {
        String configured = System.getProperty(CHECKOUT_URL_FILE_PROPERTY, "").trim();
        assertThat(configured).as(CHECKOUT_URL_FILE_PROPERTY).isNotBlank();
        Path outputFile = Path.of(configured).toAbsolutePath().normalize();
        Path temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        assertThat(outputFile).as("checkout URL output file must be under java.io.tmpdir")
                .startsWith(temporaryDirectory);
        assertThat(outputFile.getParent()).isNotNull();
        return outputFile;
    }

    /**
     * 构造商户和子商户信息。
     *
     * @return 商户信息
     */
    private Map<String, Object> merchantInfo() {
        Map<String, Object> subMerchantInfo = new LinkedHashMap<>();
        subMerchantInfo.put("subName", "Codex");
        subMerchantInfo.put("subCompanyName", "Codex Test Store");
        subMerchantInfo.put("subId", "SUB200045");
        subMerchantInfo.put("subPostal", "200000");
        subMerchantInfo.put("subStreet", "100 Test Street");
        subMerchantInfo.put("subCity", "Shanghai");
        subMerchantInfo.put("subState", "SH");
        subMerchantInfo.put("subCountryCode", "CHN");
        subMerchantInfo.put("subEmail", "codex@example.com");
        subMerchantInfo.put("subPhone", "+8613812345678");
        subMerchantInfo.put("merchantCategory", "4077");
        return Map.of(
                "merchantId", MERCHANT_ID,
                "subMerchantInfo", subMerchantInfo
        );
    }

    /**
     * 构造持卡人账单信息。
     *
     * @return 持卡人账单信息
     */
    private Map<String, Object> billingCardHolderInfo() {
        return Map.of(
                "firstName", "Codex",
                "lastName", "Tester",
                "phone", "+8613812345678",
                "email", "codex@example.com",
                "country", "CHN",
                "state", "SH",
                "city", "Shanghai",
                "street", "100 Test Street",
                "postal", "200000"
        );
    }

    /**
     * 本地 DB 中的商户 OpenAPI 密钥材料。
     *
     * @param merchantKey 商户 JWT HS256 密钥
     * @param platformPublicKeyX509Base64 平台请求体加密公钥
     * @param merchantResponsePrivateKeyPkcs8Base64 商户响应解密私钥
     */
    private record MerchantLiveSecurityMaterial(String merchantKey,
                                                String platformPublicKeyX509Base64,
                                                String merchantResponsePrivateKeyPkcs8Base64) {
    }

    /**
     * OpenAPI 真实 HTTP 响应摘要。
     *
     * @param identity 商户请求标识
     * @param httpStatus HTTP 状态
     * @param code 平台业务码
     * @param message 平台业务描述
     * @param encryptedData 加密响应 data
     */
    private record OpenApiLiveResponse(RequestIdentity identity,
                                       int httpStatus,
                                       String code,
                                       String message,
                                       String encryptedData) {

        /**
         * 构造适合日志输出的脱敏摘要。
         *
         * @return 安全响应摘要
         */
        private Map<String, Object> safeSummary() {
            return Map.of(
                    "orderNo", identity.orderNo(),
                    "orderId", identity.orderId(),
                    "httpStatus", httpStatus,
                    "code", code,
                    "message", message,
                    "encryptedDataLength", encryptedData == null ? 0 : encryptedData.length()
            );
        }
    }

    /**
     * 商户侧订单标识。
     *
     * @param orderNo 商户业务订单号
     * @param orderId 商户本次请求唯一标识
     */
    private record RequestIdentity(String orderNo, String orderId) {
    }

    /**
     * MPGS 官方基础测试卡。
     *
     * @param cardBrand 官方测试卡品牌，响应卡品牌应由平台按卡 BIN 识别后返回
     * @param cardNo 测试卡号
     * @param threeDsEnrolled 官方文档中的 3D 验证注册标识
     */
    private record OfficialTestCard(String cardBrand, String cardNo, String threeDsEnrolled) {
    }

    /**
     * 多币种验收用例。
     *
     * @param currency ISO 4217 币种
     * @param paymentAmount 支付金额
     * @param authorizationAmount 授权金额
     */
    private record CurrencyCase(String currency, String paymentAmount, String authorizationAmount) {
    }

    /**
     * 预期由内风控拒绝的 OpenAPI 场景。
     *
     * @param path OpenAPI 请求路径
     * @param orderSuffix 商户订单号场景后缀
     * @param transactionType 预期交易类型
     */
    private record RiskBlockedApiCase(String path, String orderSuffix, String transactionType) {
    }

    /**
     * 真实交易落库结果摘要。
     *
     * @param merchantOrderNo 商户业务订单号
     * @param merchantOrderId 商户本次请求唯一标识
     * @param transactionId 平台当前交易 ID
     * @param sourceTransactionId 原平台交易 ID
     * @param transactionType 交易类型
     * @param status 交易状态
     * @param labelAmount 标签金额
     * @param labelCurrency 标签币种
     * @param transactionAmount 交易金额
     * @param transactionCurrency 交易币种
     * @param transactionRate 标签币种到交易币种汇率
     * @param channelCode 渠道编码
     * @param channelOrderNo 渠道订单号
     * @param channelTransactionId 渠道交易 ID
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @param authCode 授权码
     * @param transactionDateTime 交易时间
     */
    private record LiveOperationResult(String merchantOrderNo,
                                       String merchantOrderId,
                                       String transactionId,
                                       String sourceTransactionId,
                                       String transactionType,
                                       String status,
                                       BigDecimal labelAmount,
                                       String labelCurrency,
                                       BigDecimal transactionAmount,
                                       String transactionCurrency,
                                       BigDecimal transactionRate,
                                       String channelCode,
                                       String channelOrderNo,
                                       String channelTransactionId,
                                       String channelResponseCode,
                                       String channelResponseMessage,
                                       String authCode,
                                       String transactionDateTime) {
    }
}
