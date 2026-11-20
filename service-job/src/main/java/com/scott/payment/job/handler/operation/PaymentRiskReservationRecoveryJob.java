package com.scott.payment.job.handler.operation;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
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

/** 仅负责 Job 侧定时触发，预占事实由 Risk 确认或保留。 */
@Component
public class PaymentRiskReservationRecoveryJob implements JobHandler {

    public static final String JOB_CODE = "MERCHANT_PAYMENT_RISK_RESERVATION_RECOVERY";
    public static final String HANDLER_CODE = "merchantPaymentRiskReservationRecovery";
    private static final String PATH = "/internal/risk/merchant-limit-reservations/reconcile";
    private static final String CALLER = "service-job";
    private static final String TARGET = "service-risk";
    private final RestTemplate transport;
    private final String secret;

    public PaymentRiskReservationRecoveryJob(
            @Qualifier("jobAdminInternalLoadBalancedRestTemplate") RestTemplate transport,
            @Value("${job.risk-reconciliation.internal-secret:}") String secret) {
        this.transport = transport;
        this.secret = secret;
    }

    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(HANDLER_CODE, "商户累计限额预占复核",
                "operations", "按权威支付事实复核原累计限额预占");
    }

    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        if (secret.isBlank()) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE);
        }
        long now = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String payload = "{}";
        String digest = InternalServiceSignature.payloadSha256(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalServiceSignature.HEADER_CALLER, CALLER);
        headers.set(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(now));
        headers.set(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.set(InternalServiceSignature.HEADER_SIGNATURE,
                InternalServiceSignature.sign("POST", PATH, now, nonce, CALLER, digest, secret));
        String body = transport.exchange(URI.create("http://" + TARGET + PATH), HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class).getBody();
        var result = JsonUtils.parseObject(body, new TypeReference<CommonResult<Summary>>() { });
        if (result == null || !CommonResult.isSuccess(result) || result.getData() == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY);
        }
        return JobExecuteResult.success("merchant limit reservations reconciled", result.getData());
    }

    /** 只记录批次处理数量。 */
    public record Summary(int reserved, int confirmed, int cancelled, int retained) {
    }
}
