package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelReversalRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import com.scott.payment.component.core.json.JsonUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelClientAllApiTests
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 渠道客户端 API 测试，使用本地 fake MPGS server 覆盖 PAY、AUTHORIZE、CAPTURE、REFUND、VOID、RETRIEVE 和 UPDATE_AUTHORIZATION 请求、响应和脱敏日志。
 * @status : create
 */
@Slf4j
@ExtendWith(OutputCaptureExtension.class)
class MpgsPaymentChannelClientAllApiTests {

    /**
     * TEST CARD NO，用于保存 Mpgs Payment Channel Client All API Tests 中与 testcardno 相关的业务属性。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TEST_CARD_NO = "5123450000000008";

    /**
     * TEST MASKED CARD NO，用于保存 Mpgs Payment Channel Client All API Tests 中与 test脱敏cardno 相关的业务属性。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TEST_MASKED_CARD_NO = "512345xxxxxx0008";

    /**
     * TEST CVV，用于保存 Mpgs Payment Channel Client All API Tests 中与 testcvv 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；高敏感字段，禁止明文打印日志，禁止写入异常消息。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TEST_CVV = "100";

    /**
     * TEST AUTHENTICATION TOKEN，用于保存 Mpgs Payment Channel Client All API Tests 中与 testauthenticationtoken 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TEST_AUTHENTICATION_TOKEN = "AAABBIIFmAAAAAAAAAAAAAAAAAA=";

    /**
     * TEST MERCHANT ID，用于定位 Mpgs Payment Channel Client All API Tests 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TEST_MERCHANT_ID = "TESTMID";

    /**
     * TEST USERNAME，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TEST_USERNAME = "merchant.TESTMID";

    /**
     * TEST PASSWORD，用于保存 Mpgs Payment Channel Client All API Tests 中与 testpassword 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；高敏感字段，禁止明文打印日志，禁止写入异常消息。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TEST_PASSWORD = "local-test-secret";

    /**
     * server，用于保存 Mpgs Payment Channel Client All API Tests 中与 server 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private HttpServer server;

    /**
     * requests，用于保存 Mpgs Payment Channel Client All API Tests 中与 requests 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<RecordedRequest> requests;

    /**
     * client 依赖，用于 Mpgs Payment Channel Client All API Tests 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private MpgsPaymentChannelClient client;

    /**
     * next Http Status，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private volatile int nextHttpStatus;

    /**
     * next Response Body，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private volatile String nextResponseBody;

    /**
     * 启动本地 fake MPGS server，避免默认测试依赖外部网络和真实渠道凭据。
     *
     * @throws IOException 本地端口监听失败
     */
    @BeforeEach
    void setUp() throws IOException {
        requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleMpgsRequest);
        server.start();

        MpgsChannelProperties properties = new MpgsChannelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/rest");
        properties.setVersion("100");
        properties.setMerchantId(TEST_MERCHANT_ID);
        properties.setApiUsername(TEST_USERNAME);
        properties.setApiPassword(TEST_PASSWORD);

        MpgsApiClient apiClient = new MpgsApiClient(properties, new MpgsRequestMapper(), new MpgsResponseMapper());
        client = new MpgsPaymentChannelClient(apiClient);
    }

    /**
     * 关闭本地 fake MPGS server，释放端口。
     */
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 验证 MPGS PAY 一步支付接口的 HTTP 方法、URL、Basic Auth、请求体和响应映射，并确认日志只输出脱敏卡数据。
     *
     * @param output JUnit 捕获的日志输出，用于断言敏感字段不会明文出现
     */
    @Test
    void shouldCallPayApiAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelPaymentRequest request = cardRequest(new ChannelPaymentRequest(), ChannelCapability.PAYMENT, "PAY");
        logCaseStart("PAY一步支付", request);

        ChannelPaymentResponse response = client.payment(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.PAY);
        assertSuccessResponse(response, request, MpgsApiOperation.PAY, 201);
        logCaseEnd("PAY一步支付", response, recordedRequest);
        assertMaskedLogs(output, MpgsApiOperation.PAY);
    }

    /**
     * 验证 MPGS AUTHORIZE 授权接口的渠道调用链路，重点覆盖授权请求体、成功响应映射和脱敏日志。
     *
     * @param output JUnit 捕获的日志输出，用于断言卡号、CVV 和认证 token 已脱敏
     */
    @Test
    void shouldCallAuthorizeApiAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelAuthorizeRequest request = cardRequest(new ChannelAuthorizeRequest(), ChannelCapability.AUTHORIZATION, "AUTH");
        logCaseStart("AUTHORIZE授权", request);

        ChannelPaymentResponse response = client.authorize(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.AUTHORIZE);
        assertSuccessResponse(response, request, MpgsApiOperation.AUTHORIZE, 201);
        logCaseEnd("AUTHORIZE授权", response, recordedRequest);
        assertMaskedLogs(output, MpgsApiOperation.AUTHORIZE);
    }

    /**
     * 验证平台 PRE_AUTHORIZATION 在 MPGS 中按 AUTHORIZE 提交，确保预授权语义由平台状态机区分、渠道侧只做 API 映射。
     *
     * @param output JUnit 捕获的日志输出，用于断言渠道请求响应日志安全
     */
    @Test
    void shouldCallPreAuthorizeApiAsAuthorizeAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelPreAuthorizeRequest request = cardRequest(new ChannelPreAuthorizeRequest(), ChannelCapability.PRE_AUTHORIZATION, "PREAUTH");
        logCaseStart("PRE_AUTHORIZATION预授权", request);

        ChannelPaymentResponse response = client.preAuthorize(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.AUTHORIZE);
        assertSuccessResponse(response, request, MpgsApiOperation.AUTHORIZE, 201);
        logCaseEnd("PRE_AUTHORIZATION预授权", response, recordedRequest);
        assertMaskedLogs(output, MpgsApiOperation.AUTHORIZE);
    }

    /**
     * 验证 MPGS CAPTURE 请款接口不携带卡信息，只提交交易金额和币种，并把 acquirerCode=00 的响应映射为成功。
     *
     * @param output JUnit 捕获的日志输出，用于断言请求响应日志不泄露敏感值
     */
    @Test
    void shouldCallCaptureApiAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelCaptureRequest request = baseRequest(new ChannelCaptureRequest(), ChannelCapability.CAPTURE, "CAPTURE");
        logCaseStart("CAPTURE请款", request);

        ChannelPaymentResponse response = client.capture(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.CAPTURE);
        assertSuccessResponse(response, request, MpgsApiOperation.CAPTURE, 201);
        logCaseEnd("CAPTURE请款", response, recordedRequest);
        assertMaskedLogsWithoutCard(output, MpgsApiOperation.CAPTURE);
    }

    /**
     * 验证平台 PRE_AUTH_COMPLETION 在 MPGS 中按 CAPTURE 提交，避免渠道库混入平台交易状态机逻辑。
     *
     * @param output JUnit 捕获的日志输出，用于断言日志上下文和脱敏结果
     */
    @Test
    void shouldCallPreAuthCompletionApiAsCaptureAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelCaptureRequest request = baseRequest(new ChannelCaptureRequest(), ChannelCapability.PRE_AUTH_COMPLETION, "PAC");
        logCaseStart("PRE_AUTH_COMPLETION预授权完成", request);

        ChannelPaymentResponse response = client.capture(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.CAPTURE);
        assertSuccessResponse(response, request, MpgsApiOperation.CAPTURE, 201);
        logCaseEnd("PRE_AUTH_COMPLETION预授权完成", response, recordedRequest);
        assertMaskedLogsWithoutCard(output, MpgsApiOperation.CAPTURE);
    }

    /**
     * 验证 MPGS REFUND 退款接口的金额请求体和成功响应映射，确保退款日志可追踪且不输出卡敏感信息。
     *
     * @param output JUnit 捕获的日志输出，用于断言敏感字段不会明文出现
     */
    @Test
    void shouldCallRefundApiAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelRefundRequest request = baseRequest(new ChannelRefundRequest(), ChannelCapability.REFUND, "REFUND");
        logCaseStart("REFUND退款", request);

        ChannelPaymentResponse response = client.refund(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.REFUND);
        assertSuccessResponse(response, request, MpgsApiOperation.REFUND, 201);
        logCaseEnd("REFUND退款", response, recordedRequest);
        assertMaskedLogsWithoutCard(output, MpgsApiOperation.REFUND);
    }

    /**
     * 验证 MPGS UPDATE_AUTHORIZATION 增量授权接口请求映射；真实渠道是否支持由联网测试和渠道响应决定。
     *
     * @param output JUnit 捕获的日志输出，用于断言本地成功响应链路和脱敏日志
     */
    @Test
    void shouldCallUpdateAuthorizationApiAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelIncrementalAuthorizeRequest request = baseRequest(
                new ChannelIncrementalAuthorizeRequest(),
                ChannelCapability.INCREMENTAL_AUTHORIZATION,
                "INCAUTH"
        );
        logCaseStart("UPDATE_AUTHORIZATION增量授权", request);

        ChannelPaymentResponse response = client.incrementalAuthorize(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.UPDATE_AUTHORIZATION);
        assertSuccessResponse(response, request, MpgsApiOperation.UPDATE_AUTHORIZATION, 201);
        logCaseEnd("UPDATE_AUTHORIZATION增量授权", response, recordedRequest);
        assertMaskedLogsWithoutCard(output, MpgsApiOperation.UPDATE_AUTHORIZATION);
    }

    /**
     * 验证 MPGS 返回 400 但响应体为标准 JSON 错误时，客户端应映射为渠道失败响应而不是抛网络异常。
     *
     * @param output JUnit 捕获的日志输出，用于断言错误响应日志不包含 Basic Auth 或其他敏感字段
     */
    @Test
    void shouldMapMpgsJsonErrorWhenHttpStatusIsBadRequest(CapturedOutput output) {
        nextHttpStatus = 400;
        nextResponseBody = "{\"error\":{\"cause\":\"INVALID_REQUEST\","
                + "\"explanation\":\"Update Authorization for a card payment is not supported.\"},"
                + "\"result\":\"ERROR\"}";
        ChannelIncrementalAuthorizeRequest request = baseRequest(
                new ChannelIncrementalAuthorizeRequest(),
                ChannelCapability.INCREMENTAL_AUTHORIZATION,
                "INCAUTH-ERROR"
        );
        logCaseStart("UPDATE_AUTHORIZATION增量授权渠道拒绝", request);

        ChannelPaymentResponse response = client.incrementalAuthorize(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.UPDATE_AUTHORIZATION);
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Update Authorization for a card payment is not supported.");
        logCaseEnd("UPDATE_AUTHORIZATION增量授权渠道拒绝", response, recordedRequest);
        assertMaskedLogsWithoutCard(output, MpgsApiOperation.UPDATE_AUTHORIZATION);
    }

    /**
     * 验证 MPGS VOID 撤销接口必须携带 targetTransactionId，确保撤销动作关联到同一订单生命周期中的原始交易。
     *
     * @param output JUnit 捕获的日志输出，用于断言撤销请求和响应日志脱敏
     */
    @Test
    void shouldCallVoidApiAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelVoidRequest request = baseRequest(new ChannelVoidRequest(), ChannelCapability.VOID, "VOID");
        request.getExtension().put("targetTransactionId", "CH-AUTH-ORIGINAL");
        logCaseStart("VOID撤销", request);

        ChannelPaymentResponse response = client.voidPayment(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.VOID);
        assertThat(recordedRequest.body()).contains("\"targetTransactionId\":\"CH-AUTH-ORIGINAL\"");
        assertSuccessResponse(response, request, MpgsApiOperation.VOID, 201);
        logCaseEnd("VOID撤销", response, recordedRequest);
        assertMaskedLogsWithoutCard(output, MpgsApiOperation.VOID);
    }

    /**
     * 验证平台 REVERSAL 冲正在 MPGS 中按 VOID 提交，目标交易号可从扩展字段 targetTransactionId 读取。
     *
     * @param output JUnit 捕获的日志输出，用于断言冲正请求日志可追踪且安全
     */
    @Test
    void shouldCallReversalApiAsVoidAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelReversalRequest request = baseRequest(new ChannelReversalRequest(), ChannelCapability.REVERSAL, "REVERSAL");
        request.getExtension().put("targetTransactionId", "TX-PAY-ORIGINAL");
        logCaseStart("REVERSAL冲正", request);

        ChannelPaymentResponse response = client.reversal(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertPutRequest(recordedRequest, request, MpgsApiOperation.VOID);
        assertThat(recordedRequest.body()).contains("\"targetTransactionId\":\"TX-PAY-ORIGINAL\"");
        assertSuccessResponse(response, request, MpgsApiOperation.VOID, 201);
        logCaseEnd("REVERSAL冲正", response, recordedRequest);
        assertMaskedLogsWithoutCard(output, MpgsApiOperation.VOID);
    }

    /**
     * 验证 MPGS RETRIEVE 查询接口使用 GET 且无请求体，查询响应按统一渠道响应返回。
     *
     * @param output JUnit 捕获的日志输出，用于断言查询日志不会输出空指针或敏感认证头
     */
    @Test
    void shouldCallRetrieveApiByGetAndPrintMaskedRequestResponseLogs(CapturedOutput output) {
        ChannelQueryRequest request = baseRequest(new ChannelQueryRequest(), ChannelCapability.QUERY, "QUERY");
        logCaseStart("RETRIEVE查询", request);

        ChannelPaymentResponse response = client.query(request);
        RecordedRequest recordedRequest = onlyRequest();

        assertThat(recordedRequest.method()).isEqualTo("GET");
        assertThat(recordedRequest.path()).contains("/version/100/merchant/TESTMID/order/" + request.getChannelOrderNo()
                + "/transaction/" + request.getChannelTransactionId());
        assertThat(recordedRequest.body()).isEmpty();
        assertBasicAuth(recordedRequest);
        assertSuccessResponse(response, request, MpgsApiOperation.RETRIEVE, 200);
        logCaseEnd("RETRIEVE查询", response, recordedRequest);
        assertMaskedLogsWithoutCard(output, MpgsApiOperation.RETRIEVE);
    }

    /**
     * 处理本地 fake MPGS 请求。
     * <p>
     * 该方法只用于单元测试验证渠道客户端的 HTTP 交互，不连接真实 MPGS；Authorization 只进入内存断言，不写日志。
     *
     * @param exchange JDK 内置 HTTP server 的请求响应上下文
     * @throws IOException 响应写入失败
     */
    private void handleMpgsRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        RecordedRequest recordedRequest = new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                authorization,
                body
        );
        requests.add(recordedRequest);
        log.info("MPGS本地Fake服务收到请求，request: {}",
                JsonUtils.toJsonString(new LogRequest(recordedRequest.method(), recordedRequest.path(),
                        authorization != null, toMaskedJsonLogObject(body))));

        String response = nextResponseBody == null ? successResponse(recordedRequest) : nextResponseBody;
        log.info("MPGS本地Fake服务返回响应，response: {}",
                JsonUtils.toJsonString(new LogResponse(nextHttpStatus > 0 ? nextHttpStatus : defaultHttpStatus(recordedRequest),
                        toMaskedJsonLogObject(response))));
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        int httpStatus = nextHttpStatus > 0 ? nextHttpStatus : defaultHttpStatus(recordedRequest);
        exchange.sendResponseHeaders(httpStatus, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    /**
     * 构造本地 fake MPGS 成功响应。
     * <p>
     * 响应必须包含 response.acquirerCode=00，避免测试只依赖 result=SUCCESS 误判交易成功。
     *
     * @param request 本地记录的渠道请求
     * @return MPGS 风格 JSON 响应
     */
    private String successResponse(RecordedRequest request) {
        String orderId = segmentAfter(request.path(), "order");
        String transactionId = segmentAfter(request.path(), "transaction");
        String operation = "GET".equals(request.method()) ? MpgsApiOperation.RETRIEVE : apiOperation(request.body());
        return "{"
                + "\"result\":\"SUCCESS\","
                + "\"gatewayEntryPoint\":\"WEB_SERVICES_API\","
                + "\"merchant\":\"" + TEST_MERCHANT_ID + "\","
                + "\"version\":\"100\","
                + "\"response\":{\"gatewayCode\":\"APPROVED\",\"acquirerCode\":\"00\","
                + "\"acquirerMessage\":\"Approved\","
                + "\"cardSecurityCode\":{\"gatewayCode\":\"MATCH\",\"acquirerCode\":\"M\"}},"
                + "\"order\":{\"id\":\"" + orderId + "\",\"amount\":\"10.25\",\"currency\":\"USD\",\"status\":\"AUTHORIZED\",\"reference\":\"" + orderId + "\"},"
                + "\"transaction\":{\"id\":\"" + transactionId + "\",\"type\":\"" + operation + "\",\"authorizationCode\":\"123456\",\"receipt\":\"RCPT001\"},"
                + "\"sourceOfFunds\":{\"provided\":{\"card\":{\"number\":\"" + TEST_MASKED_CARD_NO + "\",\"securityCode\":\"" + TEST_CVV + "\"}}},"
                + "\"authentication\":{\"threeDs\":{\"authenticationToken\":\"" + TEST_AUTHENTICATION_TOKEN + "\"}}"
                + "}";
    }

    /**
     * 构造包含卡信息的渠道测试请求。
     *
     * @param request    具体渠道请求类型
     * @param capability 渠道交易能力
     * @param suffix     用于生成测试订单号和交易号的后缀
     * @param <T>        请求类型
     * @return 已填充卡信息、3DS 摘要和基础交易字段的请求
     */
    private <T extends ChannelPaymentRequest> T cardRequest(T request, ChannelCapability capability, String suffix) {
        T target = baseRequest(request, capability, suffix);
        target.setCardNo(TEST_CARD_NO);
        target.setExpirationMonth("01");
        target.setExpirationYear("2039");
        target.setSecurityCode(TEST_CVV);
        ChannelPaymentRequest.ThreeDsInfo threeDsInfo = new ChannelPaymentRequest.ThreeDsInfo();
        threeDsInfo.setEci("05");
        threeDsInfo.setCavv(TEST_AUTHENTICATION_TOKEN);
        threeDsInfo.setDsTransactionId("DS-" + suffix);
        threeDsInfo.setThreeDsVersion("3DS2");
        target.setThreeDsInfo(threeDsInfo);
        return target;
    }

    /**
     * 构造不含卡信息的基础渠道测试请求，用于请款、退款、撤销、查询等后续交易动作。
     *
     * @param request    具体渠道请求类型
     * @param capability 渠道交易能力
     * @param suffix     用于生成测试订单号和交易号的后缀
     * @param <T>        请求类型
     * @return 已填充基础交易字段的请求
     */
    private <T extends ChannelPaymentRequest> T baseRequest(T request, ChannelCapability capability, String suffix) {
        request.setChannelCode(PaymentChannelCode.MPGS.getCode());
        request.setOperationId("OP-" + suffix);
        request.setTransactionId("TX-" + suffix);
        request.setSourceTransactionId("CH-SOURCE-" + suffix);
        request.setChannelOrderNo("TX-ROOT-" + suffix);
        request.setChannelTransactionId("CH-" + suffix);
        request.setMerchantId("M-LOCAL");
        request.setMerchantOrderNo("MER-" + suffix);
        request.setMerchantOrderId("REQ-" + suffix);
        request.setTransactionType(capability.getCode());
        request.setAmount(new BigDecimal("10.25"));
        request.setCurrency("USD");
        return request;
    }

    /**
     * 断言 MPGS 交易类接口使用 PUT，并校验 URL、apiOperation 和 Basic Auth。
     *
     * @param recordedRequest 本地 fake server 记录的请求
     * @param request         原始渠道请求
     * @param apiOperation    预期 MPGS API 操作
     */
    private void assertPutRequest(RecordedRequest recordedRequest, ChannelPaymentRequest request, String apiOperation) {
        assertThat(recordedRequest.method()).isEqualTo("PUT");
        assertThat(recordedRequest.path()).contains("/version/100/merchant/TESTMID/order/" + request.getChannelOrderNo()
                + "/transaction/" + request.getChannelTransactionId());
        assertThat(recordedRequest.body()).contains("\"apiOperation\":\"" + apiOperation + "\"");
        assertBasicAuth(recordedRequest);
    }

    /**
     * 断言 Basic Auth 已发送到渠道，但该请求头不得进入日志。
     *
     * @param recordedRequest 本地 fake server 记录的请求
     */
    private void assertBasicAuth(RecordedRequest recordedRequest) {
        String raw = TEST_USERNAME + ":" + TEST_PASSWORD;
        String expected = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        assertThat(recordedRequest.authorization()).isEqualTo(expected);
    }

    /**
     * 断言 MPGS 成功响应映射结果。
     * <p>
     * fake 响应包含 acquirerCode=00，因此渠道状态应为 SUCCESS；真实失败原因只在失败响应中保留给后台排查。
     *
     * @param response  渠道统一响应
     * @param request   原始渠道请求
     * @param operation 预期 MPGS 交易类型
     */
    private void assertSuccessResponse(ChannelPaymentResponse response, ChannelPaymentRequest request, String operation, int expectedHttpStatus) {
        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("00");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Approved");
        assertThat(response.getOperationId()).isEqualTo(request.getOperationId());
        assertThat(response.getTransactionId()).isEqualTo(request.getTransactionId());
        assertThat(response.getChannelOrderNo()).isEqualTo(request.getChannelOrderNo());
        assertThat(response.getChannelTransactionId()).isEqualTo(request.getChannelTransactionId());
        assertThat(response.getRawResponse()).containsEntry("transactionType", operation);
        assertThat(response.getRawResponse()).containsEntry("acquirerCode", "00");
        assertThat(response.getRawResponse()).containsEntry("httpStatus", String.valueOf(expectedHttpStatus));
        assertThat(response.getRawResponse()).containsEntry("httpMethod", MpgsApiOperation.RETRIEVE.equals(operation) ? "GET" : "PUT");
        assertThat(response.getRawResponse().get("requestUrlMasked")).contains("/version/100/merchant/TESTMID/order/"
                + request.getChannelOrderNo() + "/transaction/" + request.getChannelTransactionId());
        assertThat(response.getHttpStatus()).isEqualTo(expectedHttpStatus);
        assertThat(response.getHttpMethod()).isEqualTo(MpgsApiOperation.RETRIEVE.equals(operation) ? "GET" : "PUT");
        assertThat(response.getRequestUrlMasked()).contains("/version/100/merchant/TESTMID/order/"
                + request.getChannelOrderNo() + "/transaction/" + request.getChannelTransactionId());
        assertThat(response.getRequestHeaderJsonMasked()).contains("Basic ***");
        assertThat(response.getRequestHeaderJsonMasked()).doesNotContain(TEST_PASSWORD);
        assertThat(response.getResponseBodyJsonMasked()).contains("\"result\":\"SUCCESS\"");
        assertThat(response.getResponseBodyJsonMasked()).contains("\"order\"");
        assertThat(response.getResponseBodyJsonMasked()).contains("\"transaction\"");
        assertThat(response.getResponseBodyJsonMasked()).doesNotContain("\"securityCode\":\"" + TEST_CVV + "\"");
        assertThat(response.getResponseBodyJsonMasked()).doesNotContain(TEST_AUTHENTICATION_TOKEN);
        if (MpgsApiOperation.RETRIEVE.equals(operation)) {
            assertThat(response.getRequestBodyJsonMasked()).isEqualTo("{}");
        } else {
            assertThat(response.getRequestBodyJsonMasked()).contains("\"apiOperation\":\"" + operation + "\"");
            assertThat(response.getRequestBodyJsonMasked()).doesNotContain(TEST_CARD_NO);
            assertThat(response.getRequestBodyJsonMasked()).doesNotContain("\"securityCode\":\"" + TEST_CVV + "\"");
            assertThat(response.getRequestBodyJsonMasked()).doesNotContain(TEST_AUTHENTICATION_TOKEN);
        }
    }

    /**
     * 断言带卡交易日志已输出脱敏后的卡号、CVV 和认证 token，且没有明文敏感信息。
     *
     * @param output    JUnit 捕获的日志输出
     * @param operation 预期 MPGS API 操作
     */
    private void assertMaskedLogs(CapturedOutput output, String operation) {
        String logs = assertMaskedLogsWithoutCard(output, operation);
        assertThat(logs).contains("512345******0008");
        assertThat(logs).contains("\"securityCode\":\"***\"");
        assertThat(logs).contains("\"authenticationToken\":\"***\"");
    }

    /**
     * 断言无卡交易日志不包含密码、Basic Auth、完整卡号、CVV 或 3DS token。
     *
     * @param output    JUnit 捕获的日志输出
     * @param operation 预期 MPGS API 操作
     * @return 合并后的日志文本，供有卡场景继续断言脱敏结果
     */
    private String assertMaskedLogsWithoutCard(CapturedOutput output, String operation) {
        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("MPGS渠道请求上下文");
        assertThat(logs).contains("MPGS渠道请求报文");
        assertThat(logs).contains("MPGS渠道响应上下文");
        assertThat(logs).contains("MPGS渠道响应报文");
        assertThat(logs).contains("\"operation\":\"" + operation + "\"");
        if (MpgsApiOperation.RETRIEVE.equals(operation)) {
            assertThat(logs).contains("request: {}");
        } else {
            assertThat(logs).contains("request: {\"apiOperation\":\"" + operation + "\"");
        }
        assertThat(logs).contains("response: {");
        assertThat(logs).doesNotContain("\"request\":\"{\\\"");
        assertThat(logs).doesNotContain("\"response\":\"{\\\"");
        assertThat(logs).doesNotContain(TEST_CARD_NO);
        assertThat(logs).doesNotContain("\"securityCode\":\"" + TEST_CVV + "\"");
        assertThat(logs).doesNotContain(TEST_AUTHENTICATION_TOKEN);
        assertThat(logs).doesNotContain(TEST_PASSWORD);
        assertThat(logs).doesNotContain("Basic " + Base64.getEncoder()
                .encodeToString((TEST_USERNAME + ":" + TEST_PASSWORD).getBytes(StandardCharsets.UTF_8)));
        return logs;
    }

    /**
     * 读取本次测试唯一一次渠道请求，避免一个用例误发多笔渠道交易。
     *
     * @return 本地 fake server 记录的唯一请求
     */
    private RecordedRequest onlyRequest() {
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    /**
     * 打印测试用例开始日志。
     * <p>
     * 结构化字段通过 JsonUtils 输出，便于从日志中直接提取；卡号只输出脱敏值。
     *
     * @param caseName 测试场景名称
     * @param request  渠道请求
     */
    private void logCaseStart(String caseName, ChannelPaymentRequest request) {
        log.info("MPGS API测试开始，case: {}, request: {}", caseName, JsonUtils.toJsonString(new LogCaseRequest(
                request.getTransactionType(), request.getOperationId(), request.getTransactionId(),
                request.getChannelOrderNo(), request.getChannelTransactionId(), request.getMerchantOrderNo(),
                request.getMerchantOrderId(), String.valueOf(request.getAmount()), request.getCurrency(),
                MpgsApiClient.maskMpgsJson("{\"number\":\"" + request.getCardNo() + "\"}")
        )));
    }

    /**
     * 打印测试用例完成日志。
     *
     * @param caseName        测试场景名称
     * @param response        渠道统一响应
     * @param recordedRequest 本地 fake server 记录的请求摘要
     */
    private void logCaseEnd(String caseName, ChannelPaymentResponse response, RecordedRequest recordedRequest) {
        log.info("MPGS API测试完成，case: {}, result: {}", caseName,
                JsonUtils.toJsonString(new LogCaseResult(recordedRequest.method(), recordedRequest.path(), response)));
    }

    /**
     * 从 URL path 中提取指定路径段后面的值。
     *
     * @param path        请求路径
     * @param segmentName 路径段名称，例如 order 或 transaction
     * @return 路径段值，找不到时返回空字符串
     */
    private String segmentAfter(String path, String segmentName) {
        String marker = "/" + segmentName + "/";
        int start = path.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = path.indexOf('/', valueStart);
        return valueEnd < 0 ? path.substring(valueStart) : path.substring(valueStart, valueEnd);
    }

    /**
     * 从请求体中解析 MPGS apiOperation。
     *
     * @param body 请求 JSON
     * @return apiOperation，无法解析时返回空字符串
     */
    private String apiOperation(String body) {
        MpgsRequestPayload payload = JsonUtils.parseObject(body, MpgsRequestPayload.class);
        return payload == null ? "" : payload.getApiOperation();
    }

    private int defaultHttpStatus(RecordedRequest request) {
        return "GET".equals(request.method()) ? 200 : 201;
    }

    /**
     * 将本地 fake server 的请求/响应报文转为脱敏 JSON 对象，避免测试日志出现嵌套 JSON 字符串。
     *
     * @param json 原始请求或响应 JSON
     * @return 可直接复制到 JSON 工具的脱敏对象
     */
    private Object toMaskedJsonLogObject(String json) {
        String masked = MpgsApiClient.maskMpgsJson(json);
        if (masked == null || masked.isBlank()) {
            return Collections.emptyMap();
        }
        return JsonUtils.parseObject(masked, Object.class);
    }

    private record LogRequest(String method, String path, boolean hasAuthorization, Object request) {
    }

    private record LogResponse(int httpStatus, Object response) {
    }

    private record LogCaseRequest(String transactionType,
                                  String operationId,
                                  String transactionId,
                                  String channelOrderNo,
                                  String channelTransactionId,
                                  String merchantOrderNo,
                                  String merchantOrderId,
                                  String amount,
                                  String currency,
                                  String card) {
    }

    private record LogCaseResult(String method, String path, ChannelPaymentResponse response) {
    }

    /**
     * 本地 fake MPGS server 接收到的请求摘要。Authorization 只用于断言是否正确发送，不进入测试日志。
     *
     * @param method        HTTP 方法
     * @param path          请求路径
     * @param authorization Basic Auth 请求头
     * @param body          原始请求体，日志输出前必须脱敏
     */
    private record RecordedRequest(String method, String path, String authorization, String body) {
    }
}
