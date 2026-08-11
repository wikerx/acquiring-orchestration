package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证首次交易本地准备在风控和 MID 路由前补齐服务端卡品牌。 */
class DefaultPaymentTransactionPreparationServiceTests {

    @Test
    void shouldEnrichCardBrandBeforeRiskAndRouteProcessing() {
        PaymentCheckoutCardCapabilityService cardCapabilityService = mock(PaymentCheckoutCardCapabilityService.class);
        PaymentRiskInvokeService riskInvokeService = mock(PaymentRiskInvokeService.class);
        PaymentChannelRouteService routeService = mock(PaymentChannelRouteService.class);
        TransactionIdempotencyService idempotencyService = mock(TransactionIdempotencyService.class);
        when(idempotencyService.buildTransactionOperationKey("200045", "ORDER-ID-1", "PAYMENT"))
                .thenThrow(new IllegalStateException("stop after card brand enrichment"));
        DefaultPaymentTransactionPreparationService service = new DefaultPaymentTransactionPreparationService(
                mock(IsoDictionaryService.class),
                riskInvokeService,
                routeService,
                mock(PaymentExchangeRateService.class),
                idempotencyService,
                mock(TransactionEventOutboxService.class),
                mock(TransactionRecordService.class),
                cardCapabilityService);
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200045");
        commandDTO.setMerchantOrderId("ORDER-ID-1");

        assertThatThrownBy(() -> service.prepareInitialTransaction(commandDTO, "PAYMENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stop after card brand enrichment");

        verify(cardCapabilityService).enrichCardBrand(commandDTO);
        verifyNoInteractions(riskInvokeService, routeService);
    }
}
