package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsResponseMapperTests
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 响应映射测试，验证 result、response.acquirerCode、错误原因和 rawResponse 的统一映射规则。
 * @status : create
 */
class MpgsResponseMapperTests {

    /**
     * mapper 依赖，用于 Mpgs Response Mapper Tests 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MpgsResponseMapper mapper = new MpgsResponseMapper();

    /**
     * 验证 MPGS 交易成功响应映射：result=SUCCESS 且 response.acquirerCode=00 时，渠道状态才允许映射为 SUCCESS。
     */
    @Test
    void shouldMapSuccessResponse() {
        MpgsResponsePayload payload = successPayload("SUCCESS");

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(response.getRawChannelStatus()).isEqualTo("SUCCESS");
        assertThat(response.getChannelResponseCode()).isEqualTo("00");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Approved");
        assertThat(response.getAuthCode()).isEqualTo("123456");
        assertThat(response.getRrn()).isNull();
        assertThat(response.getAcquirerReferenceNo()).isNull();
        assertThat(response.getRawResponse()).containsEntry("transactionId", "TX-001");
        assertThat(response.getRawResponse()).containsEntry("transactionReference", "PLATFORM-REF-001");
        assertThat(response.getRawResponse()).containsEntry("acquirerReference", "ACQ001");
        assertThat(response.getRawResponse()).containsEntry("receipt", "RCPT001");
        assertThat(response.getRawResponse()).containsEntry("acquirerCode", "00");
    }

    /**
     * 验证 MPGS transaction.reference 不会被当成 ARN 返回；该字段通常是平台请求 reference 的回显。
     */
    @Test
    void shouldNotMapMpgsTransactionReferenceAsAcquirerReference() {
        MpgsResponsePayload payload = successPayload("SUCCESS");
        payload.getTransaction().setReceipt(null);
        payload.getTransaction().setAcquirer(null);

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getAcquirerReferenceNo()).isNull();
        assertThat(response.getRrn()).isNull();
        assertThat(response.getRawResponse()).containsEntry("transactionReference", "PLATFORM-REF-001");
    }

    /**
     * 验证 MPGS 网关成功但收单拒绝的场景：result=SUCCESS 不能单独代表交易成功，acquirerCode 非 00 必须映射为 FAILED。
     */
    @Test
    void shouldMapResultSuccessWithNonApprovedAcquirerCodeAsFailed() {
        MpgsResponsePayload payload = successPayload("SUCCESS");
        payload.getResponse().setGatewayCode("DECLINED");
        payload.getResponse().setAcquirerCode("14");
        payload.getResponse().setAcquirerMessage("Invalid card number");

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("14");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Invalid card number");
        assertThat(response.getRawResponse()).containsEntry("acquirerCode", "14");
        assertThat(response.getRawResponse()).containsEntry("acquirerMessage", "Invalid card number");
    }

    /**
     * 验证 MPGS UNKNOWN 结果映射：渠道结果不明确时保持 PROCESSING，交由平台状态机和查询补偿继续推进。
     */
    @Test
    void shouldMapUnknownAsProcessing() {
        MpgsResponsePayload payload = successPayload("UNKNOWN");

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.PROCESSING.getCode());
    }

    /**
     * 验证 MPGS 错误响应映射：保留渠道真实失败原因，供后台排查；商户和付款人展示文案由上层服务再做模糊化。
     */
    @Test
    void shouldMapErrorResponse() {
        MpgsResponsePayload payload = new MpgsResponsePayload();
        payload.setResult("ERROR");
        MpgsResponsePayload.ErrorPayload error = new MpgsResponsePayload.ErrorPayload();
        error.setCause("INVALID_REQUEST");
        error.setExplanation("Invalid card");
        payload.setError(error);

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.FAILED.getCode());
        assertThat(response.getChannelResponseCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Invalid card");
        assertThat(response.getRawResponse()).containsEntry("errorCause", "INVALID_REQUEST");
        assertThat(response.getRawResponse()).containsEntry("errorExplanation", "Invalid card");
    }

    /**
     * 验证真实 MPGS PAY 成功响应可以完整类型化解析，并映射订单、授权、收单、支付工具和风控摘要。
     */
    @Test
    void shouldMapRealPayResponseSummary() {
        MpgsResponsePayload payload = JsonUtils.parseObject(realPayResponseJson(), MpgsResponsePayload.class);

        ChannelPaymentResponse response = mapper.toChannelResponse(request(), payload);

        assertThat(response.getChannelTradeStatus()).isEqualTo(ChannelTradeStatus.SUCCESS.getCode());
        assertThat(response.getAuthCode()).isEqualTo("283425");
        assertThat(response.getRrn()).isNull();
        assertThat(response.getAcquirerReferenceNo()).isNull();
        assertThat(response.getChannelOrderNo()).isEqualTo("20260720162721508735");
        assertThat(response.getChannelTransactionId()).isEqualTo("20260720162721508735");
        assertThat(response.getChannelCurrency()).isEqualTo("USD");
        assertThat(response.getChannelAmount()).isEqualByComparingTo("10.01");
        assertThat(response.getPaymentMethodSummary()).isNotNull();
        assertThat(response.getPaymentMethodSummary().getPaymentBrand()).isEqualTo("MASTERCARD");
        assertThat(response.getPaymentMethodSummary().getScheme()).isEqualTo("MASTERCARD");
        assertThat(response.getPaymentMethodSummary().getFundingMethod()).isEqualTo("DEBIT");
        assertThat(response.getPaymentMethodSummary().getIssuerCountry()).isEqualTo("LBR");
        assertThat(response.getPaymentMethodSummary().getCardNumberMasked()).isEqualTo("512345xxxxxx0008");
        assertThat(response.getRawResponse()).containsEntry("orderStatus", "CAPTURED");
        assertThat(response.getRawResponse()).containsEntry("orderAmount", "10.01");
        assertThat(response.getRawResponse()).containsEntry("totalAuthorizedAmount", "10.01");
        assertThat(response.getRawResponse()).containsEntry("totalCapturedAmount", "10.01");
        assertThat(response.getRawResponse()).containsEntry("merchantCategoryCode", "4077");
        assertThat(response.getRawResponse()).containsEntry("authorizationResponseCode", "00");
        assertThat(response.getRawResponse()).containsEntry("authorizationStan", "283425");
        assertThat(response.getRawResponse()).containsEntry("authorizationTransactionIdentifier", "123456789");
        assertThat(response.getRawResponse()).containsEntry("acquirerReference", "123456789");
        assertThat(response.getRawResponse()).containsEntry("receipt", "620108283425");
        assertThat(response.getRawResponse()).containsEntry("transactionStan", "283425");
        assertThat(response.getRawResponse()).containsEntry("terminal", "2222");
        assertThat(response.getRawResponse()).containsEntry("acquirerSettlementDate", "2026-07-20");
        assertThat(response.getRawResponse()).containsEntry("acquirerTimeZone", "+0800");
        assertThat(response.getRawResponse()).containsEntry("riskGatewayCode", "ACCEPTED");
        assertThat(response.getRawResponse()).containsEntry("riskTotalScore", "10");
        assertThat(response.getRawResponse()).containsEntry("timeOfRecord", "2026-07-20T08:27:25.043Z");
        assertThat(response.getRawResponse()).containsEntry("timeOfLastUpdate", "2026-07-20T08:27:26.388Z");
    }

    private MpgsResponsePayload successPayload(String result) {
        MpgsResponsePayload payload = new MpgsResponsePayload();
        payload.setResult(result);
        MpgsResponsePayload.Response response = new MpgsResponsePayload.Response();
        response.setGatewayCode("APPROVED");
        response.setAcquirerCode("00");
        response.setAcquirerMessage("Approved");
        payload.setResponse(response);
        MpgsResponsePayload.Transaction transaction = new MpgsResponsePayload.Transaction();
        transaction.setId("TX-001");
        transaction.setAuthorizationCode("123456");
        transaction.setReceipt("RCPT001");
        transaction.setReference("PLATFORM-REF-001");
        MpgsResponsePayload.Acquirer acquirer = new MpgsResponsePayload.Acquirer();
        acquirer.setTransactionId("ACQ001");
        transaction.setAcquirer(acquirer);
        payload.setTransaction(transaction);
        return payload;
    }

    private ChannelPaymentRequest request() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setOperationId("OP-001");
        request.setTransactionId("TX-001");
        request.setMerchantOrderNo("MER-ORDER-001");
        return request;
    }

    private String realPayResponseJson() {
        return """
                {
                  "authorizationResponse": {
                    "commercialCard": "888",
                    "commercialCardIndicator": "3",
                    "financialNetworkCode": "777",
                    "posData": "1025100006600",
                    "posEntryMode": "812",
                    "processingCode": "003000",
                    "responseCode": "00",
                    "stan": "283425",
                    "transactionIdentifier": "123456789"
                  },
                  "gatewayEntryPoint": "WEB_SERVICES_API",
                  "merchant": "TESTDEVMER031",
                  "order": {
                    "amount": 10.01,
                    "authenticationStatus": "AUTHENTICATION_NOT_IN_EFFECT",
                    "chargeback": {
                      "amount": 0,
                      "currency": "USD"
                    },
                    "creationTime": "2026-07-20T08:27:25.001Z",
                    "currency": "USD",
                    "id": "20260720162721508735",
                    "lastUpdatedTime": "2026-07-20T08:27:26.388Z",
                    "merchantAmount": 10.01,
                    "merchantCategoryCode": "4077",
                    "merchantCurrency": "USD",
                    "reference": "20260720162721508735",
                    "status": "CAPTURED",
                    "totalAuthorizedAmount": 10.01,
                    "totalCapturedAmount": 10.01,
                    "totalDisbursedAmount": 0.00,
                    "totalRefundedAmount": 0.00
                  },
                  "response": {
                    "acquirerCode": "00",
                    "acquirerMessage": "Approved",
                    "gatewayCode": "APPROVED",
                    "gatewayRecommendation": "NO_ACTION"
                  },
                  "result": "SUCCESS",
                  "risk": {
                    "response": {
                      "gatewayCode": "ACCEPTED",
                      "provider": "BRIGHTERION",
                      "review": {
                        "decision": "NOT_REQUIRED"
                      },
                      "rule": [
                        {
                          "data": "NO_RULES",
                          "name": "MSO_3D_SECURE",
                          "recommendation": "NO_ACTION",
                          "type": "MSO_RULE"
                        },
                        {
                          "data": "512345",
                          "name": "MSO_BIN_RANGE",
                          "recommendation": "NO_ACTION",
                          "type": "MSO_RULE"
                        }
                      ],
                      "totalScore": 10
                    }
                  },
                  "sourceOfFunds": {
                    "provided": {
                      "card": {
                        "brand": "MASTERCARD",
                        "expiry": {
                          "month": "1",
                          "year": "39"
                        },
                        "fundingMethod": "DEBIT",
                        "issuerCountryCode": "LBR",
                        "number": "512345xxxxxx0008",
                        "scheme": "MASTERCARD",
                        "storedOnFile": "NOT_STORED"
                      }
                    },
                    "type": "CARD"
                  },
                  "timeOfLastUpdate": "2026-07-20T08:27:26.388Z",
                  "timeOfRecord": "2026-07-20T08:27:25.043Z",
                  "transaction": {
                    "acquirer": {
                      "batch": 20260720,
                      "date": "0720",
                      "id": "BOCCHINA_S2I",
                      "merchantId": "12345678",
                      "settlementDate": "2026-07-20",
                      "timeZone": "+0800",
                      "transactionId": "123456789"
                    },
                    "amount": 10.01,
                    "authenticationStatus": "AUTHENTICATION_NOT_IN_EFFECT",
                    "authorizationCode": "283425",
                    "currency": "USD",
                    "id": "20260720162721508735",
                    "receipt": "620108283425",
                    "reference": "20260720162721508735",
                    "source": "INTERNET",
                    "stan": "283425",
                    "terminal": "2222",
                    "type": "PAYMENT"
                  },
                  "version": "74"
                }
                """;
    }
}
