package com.scott.payment.openapi.support;

import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import com.scott.payment.openapi.vo.reference.CardBinLookupVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiDiagnosticLogSupportTests
 * @date : 2026-08-03
 * @email : scott_x@163.com
 * @description : 验证商户 OpenAPI 诊断摘要不会泄露代付收款账户等敏感明文。
 * @status : create
 */
class OpenApiDiagnosticLogSupportTests {

    @Test
    void shouldMaskPayoutReceiverAccountNumberInPlainRequestSummary() {
        PayoutCreateRequestDTO request = new PayoutCreateRequestDTO();
        request.setMerchantOrderNo("PAYOUT-LOG-MASK-001");
        request.setReceiverAccountNo("6222021234567890123");

        String summary = new OpenApiDiagnosticLogSupport().plainRequestSummary(request);

        assertThat(summary)
                .doesNotContain("6222021234567890123")
                .contains("6222******0123")
                .contains("PAYOUT-LOG-MASK-001");
    }

    @Test
    void shouldMaskReferenceLookupValuesInPlainSummaries() {
        IpLookupRequestDTO ipRequest = new IpLookupRequestDTO();
        ipRequest.setIpAddress("2001:4860:4860::8888");
        CardBinLookupVO cardBinResponse = new CardBinLookupVO();
        cardBinResponse.setMatched(true);
        cardBinResponse.setCardBin("411111");

        OpenApiDiagnosticLogSupport support = new OpenApiDiagnosticLogSupport();
        String requestSummary = support.plainRequestSummary(ipRequest);
        String responseSummary = support.plainResponseSummary(cardBinResponse);

        assertThat(requestSummary)
                .contains("\"ipAddress\":\"***\"")
                .doesNotContain("2001:4860:4860::8888");
        assertThat(responseSummary)
                .contains("\"cardBin\":\"***\"")
                .doesNotContain("411111");
    }
}
