package com.scott.payment.payment.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.TradeBlackTransactionFactsDTO;
import com.scott.payment.payment.api.internal.dto.TradeBlackTransactionResolveCommandDTO;
import com.scott.payment.payment.entity.TransactionLocatorDO;
import com.scott.payment.payment.entity.TransactionPaymentMethodInfoDO;
import com.scott.payment.payment.mapper.TransactionBillingInfoMapper;
import com.scott.payment.payment.mapper.TransactionLocatorMapper;
import com.scott.payment.payment.mapper.TransactionPayerInfoMapper;
import com.scott.payment.payment.mapper.TransactionPaymentMethodInfoMapper;
import com.scott.payment.payment.mapper.TransactionShippingInfoMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeBlackTransactionFactApplicationServiceTest {

    @Test
    void shouldResolveFactsFromTransactionDatasource() throws Exception {
        Method method = TradeBlackTransactionFactApplicationService.class.getMethod(
                "resolve", TradeBlackTransactionResolveCommandDTO.class);

        DS datasource = method.getAnnotation(DS.class);

        assertThat(datasource).isNotNull();
        assertThat(datasource.value()).isEqualTo(DataSourceName.TRANSACTION);
    }

    @Test
    void shouldResolveRootTransactionFactsWithoutAcceptingClientBlacklistValues() {
        TransactionLocatorMapper locatorMapper = mock(TransactionLocatorMapper.class);
        TransactionPaymentMethodInfoMapper paymentMapper = mock(TransactionPaymentMethodInfoMapper.class);
        TransactionBillingInfoMapper billingMapper = mock(TransactionBillingInfoMapper.class);
        TransactionPayerInfoMapper payerMapper = mock(TransactionPayerInfoMapper.class);
        TransactionShippingInfoMapper shippingMapper = mock(TransactionShippingInfoMapper.class);
        TradeBlackTransactionFactApplicationService service = new TradeBlackTransactionFactApplicationService(
                locatorMapper, paymentMapper, billingMapper, payerMapper, shippingMapper);
        LocalDateTime rootTime = LocalDateTime.of(2026, 11, 13, 10, 30);
        TransactionLocatorDO locator = new TransactionLocatorDO();
        locator.setTransactionId("TX-ACTION");
        locator.setRootTransactionId("TX-ROOT");
        locator.setRootTransactionDateTime(rootTime);
        locator.setMerchantId("M1001");
        locator.setMerchantOrderNo("MO-001");
        when(locatorMapper.selectByTransactionIdForAdmin("TX-ACTION")).thenReturn(locator);
        TransactionPaymentMethodInfoDO payment = new TransactionPaymentMethodInfoDO();
        payment.setCardNumberMasked("411111******1111");
        payment.setPaymentAccountHash("trusted-hash");
        payment.setCardBin("411111");
        payment.setPaymentBrand("VISA");
        payment.setIssuerCountry("US");
        when(paymentMapper.selectByTransactionId("TX-ROOT", rootTime)).thenReturn(payment);

        TradeBlackTransactionResolveCommandDTO command = command(
                TradeBlackTransactionResolveCommandDTO.IdentifierType.SYSTEM_ORDER_NO, null, " TX-ACTION ");
        TradeBlackTransactionFactsDTO.Item item = service.resolve(command).getItems().get(0);

        assertThat(item.isFound()).isTrue();
        assertThat(item.getTransactionId()).isEqualTo("TX-ROOT");
        assertThat(item.getMerchantId()).isEqualTo("M1001");
        assertThat(item.getCardNumberMasked()).isEqualTo("411111******1111");
        assertThat(item.getPaymentAccountHash()).isEqualTo("trusted-hash");
        assertThat(item.getPaymentBrand()).isEqualTo("VISA");
        verify(paymentMapper).selectByTransactionId("TX-ROOT", rootTime);
    }

    @Test
    void shouldReturnRowErrorWhenMerchantOrderHasNoLookupMerchant() {
        TradeBlackTransactionFactApplicationService service = new TradeBlackTransactionFactApplicationService(
                mock(TransactionLocatorMapper.class), mock(TransactionPaymentMethodInfoMapper.class),
                mock(TransactionBillingInfoMapper.class), mock(TransactionPayerInfoMapper.class),
                mock(TransactionShippingInfoMapper.class));

        TradeBlackTransactionFactsDTO.Item item = service.resolve(command(
                TradeBlackTransactionResolveCommandDTO.IdentifierType.MERCHANT_ORDER_NO, null, "MO-001"))
                .getItems().get(0);

        assertThat(item.isFound()).isFalse();
        assertThat(item.getErrorCode()).isEqualTo("LOOKUP_MERCHANT_REQUIRED");
    }

    private TradeBlackTransactionResolveCommandDTO command(
            TradeBlackTransactionResolveCommandDTO.IdentifierType identifierType,
            String lookupMerchantId,
            String orderNo) {
        TradeBlackTransactionResolveCommandDTO.Reference reference = new TradeBlackTransactionResolveCommandDTO.Reference();
        reference.setRowNo(1);
        reference.setIdentifierType(identifierType);
        reference.setLookupMerchantId(lookupMerchantId);
        reference.setOrderNo(orderNo);
        TradeBlackTransactionResolveCommandDTO command = new TradeBlackTransactionResolveCommandDTO();
        command.setReferences(List.of(reference));
        return command;
    }
}
