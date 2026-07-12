package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.component.core.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsApiClientLiveFlowTests
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS v100 sandbox 真实联网流程测试，覆盖授权、预授权、增量授权、请款、查询、退款和 Void；默认关闭，只有显式开启环境变量才会访问真实网关。
 * @status : create
 */
@Slf4j
class MpgsApiClientLiveFlowTests {

    private static final Set<String> SUCCESS_OR_PENDING = Set.of(
            ChannelTradeStatus.SUCCESS.getCode(),
            ChannelTradeStatus.PENDING.getCode(),
            ChannelTradeStatus.PROCESSING.getCode()
    );

    private final MpgsLiveTestConfig config = MpgsLiveTestConfig.load();

    private final MpgsApiClient client = new MpgsApiClient(
            config.toProperties(),
            new MpgsRequestMapper(),
            new MpgsResponseMapper()
    );

    /**
     * 真实联网验证授权生命周期：AUTHORIZE 成功后查询原交易，再尝试增量授权、请款和退款。
     * <p>
     * 当前测试商户对卡交易返回“Update Authorization for a card payment is not supported.”，因此增量授权按渠道失败记录，
     * 后续请款和退款回退为原授权金额继续验证完整链路。
     */
    @Test
    @EnabledIfEnvironmentVariable(named = MpgsLiveTestConfig.ENABLED_ENV, matches = "true")
    void shouldRunAuthorizeQueryUpdateCaptureAndRefundAgainstMpgsSandbox() {
        String orderId = nextId("CODXA");
        BigDecimal authorizeAmount = new BigDecimal(config.amount());
        BigDecimal updateAmount = authorizeAmount.add(new BigDecimal("0.10"));
        log.info("MPGS真实流程测试开始，flow=授权-查询-增量授权-请款-退款，context={}",
                JsonUtils.toJsonString(new FlowContext(config.maskedSummary(), orderId)));

        ChannelPaymentResponse authorize = executeAndAssert(
                "AUTHORIZE授权",
                cardRequest(new ChannelAuthorizeRequest(), ChannelCapability.AUTHORIZATION, orderId, nextId("AUT"), authorizeAmount)
        );
        String authorizeTransactionId = authorize.getTransactionNo();

        executeAndAssert(
                "QUERY查询授权",
                queryRequest(orderId, authorizeTransactionId)
        );

        ChannelPaymentResponse updateAuthorization = executeAndRecord(
                "UPDATE_AUTHORIZATION增量授权",
                amountRequest(new ChannelIncrementalAuthorizeRequest(), ChannelCapability.INCREMENTAL_AUTHORIZATION,
                        orderId, nextId("UPD"), updateAmount)
        );
        BigDecimal captureAmount = captureAmount(authorizeAmount, updateAmount, updateAuthorization);

        ChannelPaymentResponse capture = executeAndAssert(
                "CAPTURE请款",
                amountRequest(new ChannelCaptureRequest(), ChannelCapability.CAPTURE, orderId, nextId("CAP"), captureAmount)
        );

        ChannelPaymentResponse refund = executeAndAssert(
                "REFUND退款",
                amountRequest(new ChannelRefundRequest(), ChannelCapability.REFUND, orderId, nextId("REF"), captureAmount)
        );

        log.info("MPGS真实流程测试完成，flow=授权-查询-增量授权-请款-退款，result={}",
                JsonUtils.toJsonString(new AuthorizationFlowResult(summary(authorize), summary(updateAuthorization),
                        summary(capture), summary(refund))));
    }

    /**
     * 真实联网验证预授权撤销生命周期：PRE_AUTHORIZATION 成功后使用 VOID 撤销原授权交易。
     * <p>
     * 测试默认关闭，只有 MPGS_LIVE_TEST_ENABLED=true 时才访问真实 MPGS 沙箱，避免普通单元测试依赖外部网络和凭据。
     */
    @Test
    @EnabledIfEnvironmentVariable(named = MpgsLiveTestConfig.ENABLED_ENV, matches = "true")
    void shouldRunPreAuthorizeAndVoidAgainstMpgsSandbox() {
        String orderId = nextId("CODXV");
        BigDecimal amount = new BigDecimal(config.amount());
        log.info("MPGS真实流程测试开始，flow=预授权-Void，context={}",
                JsonUtils.toJsonString(new FlowContext(config.maskedSummary(), orderId)));

        ChannelPreAuthorizeRequest preAuthorizeRequest = cardRequest(
                new ChannelPreAuthorizeRequest(),
                ChannelCapability.PRE_AUTHORIZATION,
                orderId,
                nextId("PRE"),
                amount
        );
        ChannelPaymentResponse preAuthorize = executeAndAssert("PRE_AUTHORIZATION预授权", preAuthorizeRequest);

        ChannelVoidRequest voidRequest = amountRequest(new ChannelVoidRequest(), ChannelCapability.VOID, orderId, nextId("VOI"), amount);
        voidRequest.setOriginalTransactionNo(preAuthorize.getTransactionNo());
        ChannelPaymentResponse voidResponse = executeAndAssert("VOID撤销预授权", voidRequest);

        log.info("MPGS真实流程测试完成，flow=预授权-Void，result={}",
                JsonUtils.toJsonString(new VoidFlowResult(summary(preAuthorize), summary(voidResponse))));
    }

    /**
     * 执行真实 MPGS 请求并断言渠道状态为成功、处理中或待处理。
     *
     * @param caseName 测试场景名称
     * @param request  渠道请求
     * @return 渠道统一响应
     */
    private ChannelPaymentResponse executeAndAssert(String caseName, ChannelPaymentRequest request) {
        ChannelPaymentResponse response = executeAndRecord(caseName, request);
        assertThat(response.getChannelTradeStatus()).as(caseName + " channelTradeStatus").isIn(SUCCESS_OR_PENDING);
        assertThat(response.getChannelResponseCode()).as(caseName + " responseCode").isNotBlank();
        return response;
    }

    /**
     * 执行真实 MPGS 请求并记录脱敏后的请求响应摘要。
     *
     * @param caseName 测试场景名称
     * @param request  渠道请求
     * @return 渠道统一响应
     */
    private ChannelPaymentResponse executeAndRecord(String caseName, ChannelPaymentRequest request) {
        log.info("MPGS真实接口开始，case={}, request={}", caseName, JsonUtils.toJsonString(new LiveCaseRequest(
                request.getTransactionType(), request.getMerchantOrderNo(), request.getTransactionNo(),
                String.valueOf(request.getAmount()), request.getCurrency(),
                MpgsApiClient.maskMpgsJson("{\"number\":\"" + request.getCardNo() + "\"}")
        )));
        ChannelPaymentResponse response = client.execute(request);
        log.info("MPGS真实接口完成，case={}, response={}", caseName, JsonUtils.toJsonString(response));
        assertThat(response.getChannelResponseCode()).as(caseName + " responseCode").isNotBlank();
        return response;
    }

    /**
     * 决定请款金额。
     * <p>
     * 如果增量授权成功，按增量后的金额请款；如果渠道明确返回不支持卡交易增量授权，则按原授权金额继续验证请款和退款。
     *
     * @param authorizeAmount     原授权金额
     * @param updateAmount        增量后的目标金额
     * @param updateAuthorization 增量授权渠道响应
     * @return 后续请款和退款金额
     */
    private BigDecimal captureAmount(BigDecimal authorizeAmount,
                                     BigDecimal updateAmount,
                                     ChannelPaymentResponse updateAuthorization) {
        if (SUCCESS_OR_PENDING.contains(updateAuthorization.getChannelTradeStatus())) {
            return updateAmount;
        }
        assertThat(updateAuthorization.getChannelResponseMessage())
                .as("MPGS增量授权失败原因")
                .containsIgnoringCase("not supported");
        log.warn("MPGS真实接口返回增量授权不支持，后续按原授权金额继续请款退款，updateAuthorization={}",
                JsonUtils.toJsonString(summary(updateAuthorization)));
        return authorizeAmount;
    }

    /**
     * 构造真实联网用的卡交易请求。
     *
     * @param request       具体请求类型
     * @param capability    渠道交易能力
     * @param orderId       MPGS orderId
     * @param transactionId MPGS transactionId
     * @param amount        交易金额
     * @param <T>           请求类型
     * @return 已填充卡资料和基础交易字段的请求
     */
    private <T extends ChannelPaymentRequest> T cardRequest(T request,
                                                            ChannelCapability capability,
                                                            String orderId,
                                                            String transactionId,
                                                            BigDecimal amount) {
        fillBase(request, capability, orderId, transactionId, amount);
        request.setCardNo(config.cardNo());
        request.setExpirationMonth(config.expiryMonth());
        request.setExpirationYear(config.expiryYear());
        request.setSecurityCode(config.csc());
        return request;
    }

    /**
     * 构造真实联网用的金额类交易请求，例如请款、退款、撤销和增量授权。
     *
     * @param request       具体请求类型
     * @param capability    渠道交易能力
     * @param orderId       MPGS orderId
     * @param transactionId MPGS transactionId
     * @param amount        交易金额
     * @param <T>           请求类型
     * @return 已填充基础交易字段的请求
     */
    private <T extends ChannelPaymentRequest> T amountRequest(T request,
                                                              ChannelCapability capability,
                                                              String orderId,
                                                              String transactionId,
                                                              BigDecimal amount) {
        fillBase(request, capability, orderId, transactionId, amount);
        return request;
    }

    /**
     * 构造真实联网查询请求。
     *
     * @param orderId       MPGS orderId
     * @param transactionId 待查询的 MPGS transactionId
     * @return 查询请求
     */
    private ChannelQueryRequest queryRequest(String orderId, String transactionId) {
        ChannelQueryRequest request = new ChannelQueryRequest();
        fillBase(request, ChannelCapability.QUERY, orderId, transactionId, new BigDecimal(config.amount()));
        return request;
    }

    /**
     * 填充真实联网测试请求的公共字段。
     *
     * @param request       渠道请求
     * @param capability    渠道交易能力
     * @param orderId       MPGS orderId
     * @param transactionId MPGS transactionId
     * @param amount        交易金额
     */
    private void fillBase(ChannelPaymentRequest request,
                          ChannelCapability capability,
                          String orderId,
                          String transactionId,
                          BigDecimal amount) {
        request.setChannelCode("MPGS");
        request.setTransactionOrderNo(orderId);
        request.setTransactionNo(transactionId);
        request.setMerchantId(config.merchantId());
        request.setMerchantOrderNo(orderId);
        request.setTransactionType(capability.getCode());
        request.setAmount(amount);
        request.setCurrency(config.currency());
        request.setTransactionDateTime(LocalDateTime.now());
    }

    /**
     * 生成短交易标识，避免真实沙箱重复 transactionId。
     *
     * @param prefix 标识前缀
     * @return 带毫秒时间戳的测试标识
     */
    private String nextId(String prefix) {
        return prefix + DateTimeFormatter.ofPattern("yyMMddHHmmssSSS").format(LocalDateTime.now());
    }

    /**
     * 构造真实联网测试响应摘要，保留交易号、渠道状态、响应码和后台排查用 rawResponse。
     *
     * @param response 渠道统一响应
     * @return 响应摘要
     */
    private ResponseSummary summary(ChannelPaymentResponse response) {
        return new ResponseSummary(response.getTransactionNo(), response.getChannelTradeStatus(),
                response.getChannelResponseCode(), response.getChannelResponseMessage(), response.getRawResponse());
    }

    private record FlowContext(String config, String orderId) {
    }

    private record AuthorizationFlowResult(ResponseSummary authorize,
                                           ResponseSummary updateAuthorization,
                                           ResponseSummary capture,
                                           ResponseSummary refund) {
    }

    private record VoidFlowResult(ResponseSummary preAuthorize, ResponseSummary voidResponse) {
    }

    private record LiveCaseRequest(String transactionType,
                                   String orderId,
                                   String transactionId,
                                   String amount,
                                   String currency,
                                   String card) {
    }

    private record ResponseSummary(String transactionNo,
                                   String status,
                                   String code,
                                   String message,
                                   java.util.Map<String, String> raw) {
    }
}
