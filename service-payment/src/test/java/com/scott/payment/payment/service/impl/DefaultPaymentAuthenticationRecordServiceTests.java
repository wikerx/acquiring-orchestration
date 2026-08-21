package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.entity.TransactionAuthenticationInfoDO;
import com.scott.payment.payment.mapper.TransactionAuthenticationInfoMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultPaymentAuthenticationRecordServiceTests {

    @Test
    void shouldRouteThreeDsAuditWritesToTransactionLogicalDataSource() {
        assertThat(DefaultPaymentAuthenticationRecordService.class.getAnnotation(DS.class)).isNull();

        assertTransactionMethod("recordChannelResult",
                ChannelThreeDsAuthenticationRequest.class, ChannelThreeDsAuthenticationResponse.class);
        assertTransactionMethod("recordChannelFailure", ChannelThreeDsAuthenticationRequest.class,
                ChannelThreeDsStatus.class, String.class);
        assertTransactionMethod("recordTimeout",
                com.scott.payment.payment.entity.PaymentCheckoutAttemptDO.class);
    }

    @Test
    void shouldPersistOnlyStableSafeThreeDsPhaseSummary() {
        TransactionAuthenticationInfoMapper mapper = mock(TransactionAuthenticationInfoMapper.class);
        DefaultPaymentAuthenticationRecordService service =
                new DefaultPaymentAuthenticationRecordService(mapper);
        ChannelThreeDsAuthenticationRequest request = request(ChannelThreeDsPhase.AUTHENTICATE);
        ChannelThreeDsAuthenticationResponse response = new ChannelThreeDsAuthenticationResponse();
        response.setPhase(ChannelThreeDsPhase.AUTHENTICATE);
        response.setStatus(ChannelThreeDsStatus.CHALLENGE_REQUIRED);
        response.setAuthenticationTransactionId("3DSTX-001");
        response.setThreeDsVersion("2.2.0");
        response.setThreeDsServerTransactionId("server-001");
        response.setAcsTransactionId("acs-001");
        response.setDsTransactionId("ds-001");
        response.setEci("02");
        response.setCavv("sensitive-cavv");
        response.setRedirectHtml("<form><input name=\"creq\" value=\"secret\"></form>");
        response.setRedirectUrl("https://acs.example.test/challenge?token=secret");
        response.setRawResponseMasked("{\"cavv\":\"***\",\"payload\":\"must-not-persist\"}");

        service.recordChannelResult(request, response);

        ArgumentCaptor<TransactionAuthenticationInfoDO> captor =
                ArgumentCaptor.forClass(TransactionAuthenticationInfoDO.class);
        verify(mapper).upsertPhase(captor.capture());
        TransactionAuthenticationInfoDO row = captor.getValue();
        assertThat(row.getAuthenticationInfoId()).hasSize(64);
        assertThat(row.getTransactionId()).isEqualTo("TX-001");
        assertThat(row.getOperationId()).isEqualTo("OP-001");
        assertThat(row.getAuthenticationStatus()).isEqualTo("ATTEMPTED");
        assertThat(row.getChallengeRequired()).isEqualTo(1);
        assertThat(row.getChallengeStatus()).isEqualTo("REQUIRED");
        assertThat(row.getAuthenticationRedirectUrlHash()).hasSize(64);
        assertThat(row.getAuthenticationExtraJson()).contains("AUTHENTICATE").contains("MPGS");
        assertThat(row.getCavv()).isNull();
        assertThat(row.getAuthenticationExtraJson())
                .doesNotContain("sensitive-cavv", "creq", "secret", "must-not-persist");
        assertThat(row.getAuthenticationResultMessage())
                .doesNotContain("sensitive-cavv", "secret", "must-not-persist");
    }

    @Test
    void shouldPersistSafeProviderValidationDiagnosticsWithoutRawResponse() {
        TransactionAuthenticationInfoMapper mapper = mock(TransactionAuthenticationInfoMapper.class);
        DefaultPaymentAuthenticationRecordService service =
                new DefaultPaymentAuthenticationRecordService(mapper);
        ChannelThreeDsAuthenticationRequest request = request(ChannelThreeDsPhase.AUTHENTICATE);
        ChannelThreeDsAuthenticationResponse response = new ChannelThreeDsAuthenticationResponse();
        response.setPhase(ChannelThreeDsPhase.AUTHENTICATE);
        response.setStatus(ChannelThreeDsStatus.FAILED);
        response.setFailureCode("INVALID_REQUEST");
        response.setFailureMessage("Invalid card 5123450000000008 at sourceOfFunds.type token=secret-value");
        response.setRawResponseMasked("{\"payload\":\"must-not-persist\"}");
        response.getExtension().put("providerResult", "ERROR");
        response.getExtension().put("errorField", "sourceOfFunds.type");
        response.getExtension().put("validationType", "INVALID");
        response.getExtension().put("httpStatus", "400");

        service.recordChannelResult(request, response);

        ArgumentCaptor<TransactionAuthenticationInfoDO> captor =
                ArgumentCaptor.forClass(TransactionAuthenticationInfoDO.class);
        verify(mapper).upsertPhase(captor.capture());
        TransactionAuthenticationInfoDO row = captor.getValue();
        assertThat(row.getAuthenticationStatus()).isEqualTo("FAILED");
        assertThat(row.getAuthenticationResultCode()).isEqualTo("INVALID_REQUEST");
        assertThat(row.getAuthenticationResultMessage())
                .contains("Invalid card ***", "sourceOfFunds.type", "token=***")
                .doesNotContain("5123450000000008", "secret-value");
        assertThat(row.getAuthenticationExtraJson())
                .contains("sourceOfFunds.type", "INVALID", "ERROR", "400")
                .doesNotContain("must-not-persist");
    }

    @Test
    void shouldGenerateStableIdForRepeatedAuthenticationPhase() {
        TransactionAuthenticationInfoMapper mapper = mock(TransactionAuthenticationInfoMapper.class);
        DefaultPaymentAuthenticationRecordService service =
                new DefaultPaymentAuthenticationRecordService(mapper);
        ChannelThreeDsAuthenticationRequest request = request(ChannelThreeDsPhase.INITIALIZE);
        ChannelThreeDsAuthenticationResponse response = new ChannelThreeDsAuthenticationResponse();
        response.setPhase(ChannelThreeDsPhase.INITIALIZE);
        response.setStatus(ChannelThreeDsStatus.READY_TO_AUTHENTICATE);

        service.recordChannelResult(request, response);
        service.recordChannelResult(request, response);

        ArgumentCaptor<TransactionAuthenticationInfoDO> captor =
                ArgumentCaptor.forClass(TransactionAuthenticationInfoDO.class);
        verify(mapper, org.mockito.Mockito.times(2)).upsertPhase(captor.capture());
        List<TransactionAuthenticationInfoDO> rows = captor.getAllValues();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getAuthenticationInfoId())
                .isEqualTo(rows.get(1).getAuthenticationInfoId());
    }

    @Test
    void shouldRecordChallengeTimeoutAsTerminalVerifyFailure() {
        TransactionAuthenticationInfoMapper mapper = mock(TransactionAuthenticationInfoMapper.class);
        DefaultPaymentAuthenticationRecordService service =
                new DefaultPaymentAuthenticationRecordService(mapper);
        com.scott.payment.payment.entity.PaymentCheckoutAttemptDO attempt =
                new com.scott.payment.payment.entity.PaymentCheckoutAttemptDO();
        attempt.setTransactionId("TX-001");
        attempt.setOperationId("OP-001");
        attempt.setTransactionDateTime(LocalDateTime.of(2026, 8, 13, 16, 0));
        attempt.setChannelCode("MPGS");
        attempt.setThreeDsStatus(ChannelThreeDsStatus.CHALLENGE_REQUIRED.name());
        attempt.setThreeDsTransactionId("3DSTX-001");
        attempt.setThreeDsVersion("2.2.0");

        service.recordTimeout(attempt);

        ArgumentCaptor<TransactionAuthenticationInfoDO> captor =
                ArgumentCaptor.forClass(TransactionAuthenticationInfoDO.class);
        verify(mapper).upsertPhase(captor.capture());
        TransactionAuthenticationInfoDO row = captor.getValue();
        assertThat(row.getAuthenticationStatus()).isEqualTo("FAILED");
        assertThat(row.getAuthenticationResultCode()).isEqualTo("THREE_DS_AUTHENTICATION_TIMEOUT");
        assertThat(row.getChallengeStatus()).isEqualTo("FAILED");
        assertThat(row.getAuthenticationExtraJson()).contains("VERIFY");
        assertThat(row.getCavv()).isNull();
    }

    private ChannelThreeDsAuthenticationRequest request(ChannelThreeDsPhase phase) {
        ChannelThreeDsAuthenticationRequest request = new ChannelThreeDsAuthenticationRequest();
        request.setPhase(phase);
        request.setChannelCode("MPGS");
        request.setOperationId("OP-001");
        request.setTransactionId("TX-001");
        request.setTransactionDateTime(LocalDateTime.of(2026, 8, 13, 16, 0));
        request.setAuthenticationTransactionId("3DSTX-001");
        request.setRedirectResponseUrl("https://checkout.example.test/bridge?threeDsReturnToken=secret");
        return request;
    }

    private void assertTransactionMethod(String methodName, Class<?>... parameterTypes) {
        Method method;
        try {
            method = DefaultPaymentAuthenticationRecordService.class.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.TRANSACTION);
    }
}
