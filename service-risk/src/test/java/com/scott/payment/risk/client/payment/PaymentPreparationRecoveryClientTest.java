package com.scott.payment.risk.client.payment;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentPreparationRecoveryClientTest {
    private static final String PATH = "/internal/payment/preparation-ownership/reconcile-cancellation";
    private static final String TOKEN = "synthetic-internal-signing-material";
    private final RestTemplate transport = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(transport).build();

    @Test
    void onlyExplicitSuccessAllowsRelease() {
        for (String decision : new String[]{"RETAIN", "DEFER", "RELEASE_ALLOWED"}) {
            server.expect(requestTo("http://service-payment" + PATH))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(InternalServiceSignature.HEADER_CALLER, "service-risk"))
                    .andRespond(withSuccess(JsonUtils.toJsonString(CommonResult.success(decision)), MediaType.APPLICATION_JSON));
        }
        var client = new PaymentPreparationRecoveryClient(transport, TOKEN);
        assertThat(client.authorizesRelease(reservation())).isFalse();
        assertThat(client.authorizesRelease(reservation())).isFalse();
        assertThat(client.authorizesRelease(reservation())).isTrue();
        server.verify();
    }

    @Test
    void missingCredentialsOrUnreadableResponsePreserveReservation() {
        assertThat(new PaymentPreparationRecoveryClient(transport, "").authorizesRelease(reservation())).isFalse();
        server.expect(requestTo("http://service-payment" + PATH))
                .andRespond(withSuccess("malformed-response", MediaType.APPLICATION_JSON));
        assertThat(new PaymentPreparationRecoveryClient(transport, TOKEN).authorizesRelease(reservation())).isFalse();
        server.verify();
    }

    private MerchantLimitReservationDO reservation() {
        var reservation = new MerchantLimitReservationDO();
        reservation.setMerchantId("FIXTURE");
        reservation.setPaymentPreparationKey("a".repeat(64));
        reservation.setPaymentPreparationGeneration(2L);
        reservation.setTransactionId("SYNTHETIC-CANDIDATE");
        reservation.setPaymentTransactionDateTime(LocalDateTime.of(2026, 6, 30, 23, 59, 59, 850_000_000));
        return reservation;
    }
}
