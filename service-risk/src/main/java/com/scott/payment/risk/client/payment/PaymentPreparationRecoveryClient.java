package com.scott.payment.risk.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;

/**
 * 只有对确切准备代次取得可释放回执，Risk 才能取消已超时但没有确认交易事实的预占。
 */
@Component
@Slf4j
public class PaymentPreparationRecoveryClient {
    private static final String CALLER = "service-risk";
    private static final String PATH = "/internal/payment/preparation-ownership/reconcile-cancellation";
    private static final URI TARGET = URI.create("http://service-payment" + PATH);
    private final RestTemplate transport;
    private final String secret;

    public PaymentPreparationRecoveryClient(
            @Qualifier("riskPaymentPreparationRestTemplate") RestTemplate transport,
            @Value("${risk.payment-preparation.internal-secret:}") String secret) {
        this.transport = transport;
        this.secret = secret;
    }

    /**
     * 仅在支付服务确认这一准备代次可释放时授权风险侧取消预占。
     */
    public boolean authorizesRelease(MerchantLimitReservationDO reservation) {
        if (secret.isBlank() || reservation.getPaymentPreparationKey() == null
                || reservation.getPaymentPreparationGeneration() == null
                || reservation.getPaymentTransactionDateTime() == null) {
            return false;
        }
        String payload = JsonUtils.toJsonString(new CancellationRequest(reservation.getMerchantId(),
                reservation.getPaymentPreparationKey(), reservation.getPaymentPreparationGeneration(),
                reservation.getTransactionId(), reservation.getPaymentTransactionDateTime().toString()));
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalServiceSignature.HEADER_CALLER, CALLER);
        headers.set(InternalServiceSignature.HEADER_TIMESTAMP, Long.toString(timestamp));
        headers.set(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.set(InternalServiceSignature.HEADER_SIGNATURE, InternalServiceSignature.sign(
                "POST", PATH, timestamp, nonce, CALLER, InternalServiceSignature.payloadSha256(payload), secret));
        try {
            String body = transport.exchange(TARGET, HttpMethod.POST, new HttpEntity<>(payload, headers),
                    String.class).getBody();
            var reply = JsonUtils.parseObject(body, new TypeReference<CommonResult<String>>() { });
            return CommonResult.isSuccess(reply) && "RELEASE_ALLOWED".equals(reply.getData());
        } catch (RuntimeException failure) {
            log.warn("准备归属权威回读暂不可用, event: RISK_PAYMENT_PREPARATION_RECOVERY_DEFERRED, type: {}",
                    failure.getClass().getSimpleName());
            return false;
        }
    }

    private record CancellationRequest(String merchantId, String ownershipKey, long generation,
                                       String candidateTransactionId, String transactionDateTime) {
    }
}
