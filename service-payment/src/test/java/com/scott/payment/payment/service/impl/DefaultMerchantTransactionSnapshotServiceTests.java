package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionBillingInfoDO;
import com.scott.payment.payment.entity.TransactionMerchantSnapshotDO;
import com.scott.payment.payment.entity.TransactionPayerInfoDO;
import com.scott.payment.payment.entity.TransactionProductItemDO;
import com.scott.payment.payment.entity.TransactionShippingInfoDO;
import com.scott.payment.payment.mapper.TransactionBillingInfoMapper;
import com.scott.payment.payment.mapper.TransactionAuthenticationInfoMapper;
import com.scott.payment.payment.mapper.TransactionMerchantSnapshotMapper;
import com.scott.payment.payment.mapper.TransactionPayerInfoMapper;
import com.scott.payment.payment.mapper.TransactionProductItemMapper;
import com.scott.payment.payment.mapper.TransactionShippingInfoMapper;
import com.scott.payment.payment.service.MerchantFeeVersionSnapshotService;
import com.scott.payment.payment.service.dto.FrozenMerchantFeeVersionSnapshotDTO;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Merchant-visible transaction snapshot persistence tests. */
class DefaultMerchantTransactionSnapshotServiceTests {

    @Test
    void shouldNotInsertMerchantSnapshotWhenFeeVersionCannotBeFrozen() {
        TransactionBillingInfoMapper billingMapper = mock(TransactionBillingInfoMapper.class);
        TransactionAuthenticationInfoMapper authenticationMapper = mock(TransactionAuthenticationInfoMapper.class);
        TransactionMerchantSnapshotMapper merchantSnapshotMapper = mock(TransactionMerchantSnapshotMapper.class);
        TransactionPayerInfoMapper payerMapper = mock(TransactionPayerInfoMapper.class);
        TransactionShippingInfoMapper shippingMapper = mock(TransactionShippingInfoMapper.class);
        TransactionProductItemMapper productMapper = mock(TransactionProductItemMapper.class);
        MerchantFeeVersionSnapshotService feeSnapshotService = mock(MerchantFeeVersionSnapshotService.class);
        when(feeSnapshotService.freezeActiveVersion(anyString(), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("fee configuration unavailable"));
        DefaultMerchantTransactionSnapshotService service = new DefaultMerchantTransactionSnapshotService(
                billingMapper, authenticationMapper, merchantSnapshotMapper, payerMapper, shippingMapper,
                productMapper, feeSnapshotService);

        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setTransactionId("TX-PAYMENT-001");
        result.setOperationId("OP-001");
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 22, 0);

        assertThatThrownBy(() -> service.recordInitialSnapshots(command(), result, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fee configuration unavailable");
        verify(merchantSnapshotMapper, never()).insert(any(TransactionMerchantSnapshotDO.class));
        verify(billingMapper, never()).insert(any(TransactionBillingInfoDO.class));
    }

    @Test
    void shouldStoreMerchantSnapshotForFollowUpAction() {
        TransactionBillingInfoMapper billingMapper = mock(TransactionBillingInfoMapper.class);
        TransactionAuthenticationInfoMapper authenticationMapper = mock(TransactionAuthenticationInfoMapper.class);
        TransactionMerchantSnapshotMapper merchantSnapshotMapper = mock(TransactionMerchantSnapshotMapper.class);
        TransactionPayerInfoMapper payerMapper = mock(TransactionPayerInfoMapper.class);
        TransactionShippingInfoMapper shippingMapper = mock(TransactionShippingInfoMapper.class);
        TransactionProductItemMapper productMapper = mock(TransactionProductItemMapper.class);
        MerchantFeeVersionSnapshotService feeSnapshotService = feeSnapshotService();
        DefaultMerchantTransactionSnapshotService service = new DefaultMerchantTransactionSnapshotService(
                billingMapper, authenticationMapper, merchantSnapshotMapper, payerMapper, shippingMapper,
                productMapper, feeSnapshotService);

        PaymentCreateCommandDTO command = command();
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setTransactionId("TX-REFUND-001");
        result.setOperationId("OP-001");

        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 12, 45, 0, 123_000_000);
        service.recordActionSnapshot(command, result, now);

        ArgumentCaptor<TransactionMerchantSnapshotDO> merchantCaptor =
                ArgumentCaptor.forClass(TransactionMerchantSnapshotDO.class);
        verify(merchantSnapshotMapper).insert(merchantCaptor.capture());
        assertThat(merchantCaptor.getValue().getTransactionId()).isEqualTo("TX-REFUND-001");
        assertThat(merchantCaptor.getValue().getTransactionDateTime())
                .isEqualTo(command.getTransactionDateTime());
        assertFeeSnapshot(merchantCaptor.getValue(), now);
        verify(feeSnapshotService).freezeActiveVersion("200045", now);
    }

    @Test
    void shouldStoreMerchantVisibleSnapshotsInPlainColumnsAndJson() {
        TransactionBillingInfoMapper billingMapper = mock(TransactionBillingInfoMapper.class);
        TransactionAuthenticationInfoMapper authenticationMapper = mock(TransactionAuthenticationInfoMapper.class);
        TransactionMerchantSnapshotMapper merchantSnapshotMapper = mock(TransactionMerchantSnapshotMapper.class);
        TransactionPayerInfoMapper payerMapper = mock(TransactionPayerInfoMapper.class);
        TransactionShippingInfoMapper shippingMapper = mock(TransactionShippingInfoMapper.class);
        TransactionProductItemMapper productMapper = mock(TransactionProductItemMapper.class);
        MerchantFeeVersionSnapshotService feeSnapshotService = feeSnapshotService();
        DefaultMerchantTransactionSnapshotService service = new DefaultMerchantTransactionSnapshotService(
                billingMapper, authenticationMapper, merchantSnapshotMapper, payerMapper, shippingMapper,
                productMapper, feeSnapshotService);

        PaymentCreateCommandDTO command = command();
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setTransactionId("TX-ROOT-001");
        result.setOperationId("OP-001");
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 12, 40, 0, 123_000_000);

        service.recordInitialSnapshots(command, result, now);

        ArgumentCaptor<TransactionMerchantSnapshotDO> merchantCaptor =
                ArgumentCaptor.forClass(TransactionMerchantSnapshotDO.class);
        verify(merchantSnapshotMapper).insert(merchantCaptor.capture());
        assertThat(merchantCaptor.getValue().getSubMerchantInfoJson())
                .contains("SUB-1001", "Travel Merchant");
        assertFeeSnapshot(merchantCaptor.getValue(), now);
        verify(feeSnapshotService).freezeActiveVersion("200045", now);

        ArgumentCaptor<TransactionBillingInfoDO> billingCaptor =
                ArgumentCaptor.forClass(TransactionBillingInfoDO.class);
        verify(billingMapper).insert(billingCaptor.capture());
        assertThat(billingCaptor.getValue().getFirstName()).isEqualTo("John");
        assertThat(billingCaptor.getValue().getEmail()).isEqualTo("john@example.com");

        ArgumentCaptor<TransactionShippingInfoDO> shippingCaptor =
                ArgumentCaptor.forClass(TransactionShippingInfoDO.class);
        verify(shippingMapper).insert(shippingCaptor.capture());
        assertThat(shippingCaptor.getValue().getStreet()).isEqualTo("200 Shipping Street");

        ArgumentCaptor<TransactionPayerInfoDO> payerCaptor =
                ArgumentCaptor.forClass(TransactionPayerInfoDO.class);
        verify(payerMapper).insert(payerCaptor.capture());
        TransactionPayerInfoDO payer = payerCaptor.getValue();
        assertThat(payer.getEmail()).isEqualTo("payer@example.com");
        assertThat(payer.getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(payer.getBrowserInfoJson()).contains("Chrome", "128.0.0.0");
        assertThat(payer.getPayerEmailHash()).hasSize(64);
        assertThat(payer.getIpAddressHash()).hasSize(64);

        verify(productMapper, times(1)).insert(any(TransactionProductItemDO.class));
    }

    private MerchantFeeVersionSnapshotService feeSnapshotService() {
        MerchantFeeVersionSnapshotService service = mock(MerchantFeeVersionSnapshotService.class);
        when(service.freezeActiveVersion(anyString(), any(LocalDateTime.class))).thenAnswer(invocation -> {
            FeeVersionSnapshot snapshot = mock(FeeVersionSnapshot.class);
            when(snapshot.feePlanId()).thenReturn(1001L);
            when(snapshot.feePlanVersionId()).thenReturn(1008L);
            when(snapshot.feePlanVersionNo()).thenReturn(8);
            when(snapshot.snapshotHash()).thenReturn("a".repeat(64));
            when(snapshot.pricingLockTime()).thenReturn(invocation.getArgument(1));
            return new FrozenMerchantFeeVersionSnapshotDTO(snapshot,
                    "{\"feePlanVersionId\":1008,\"snapshotHash\":\"" + "a".repeat(64) + "\"}");
        });
        return service;
    }

    private void assertFeeSnapshot(TransactionMerchantSnapshotDO snapshot, LocalDateTime expectedTime) {
        assertThat(snapshot.getFeePlanId()).isEqualTo(1001L);
        assertThat(snapshot.getFeePlanVersionId()).isEqualTo(1008L);
        assertThat(snapshot.getFeePlanVersionNo()).isEqualTo(8);
        assertThat(snapshot.getFeeSnapshotHash()).isEqualTo("a".repeat(64));
        assertThat(snapshot.getFeeSnapshotTime()).isEqualTo(expectedTime);
        assertThat(snapshot.getFeeConfigSnapshotJson())
                .contains("\"feePlanVersionId\":1008", "\"snapshotHash\"");
    }

    private PaymentCreateCommandDTO command() {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId("200045");
        command.setMerchantOrderNo("M202608010002");
        command.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 10, 30, 0, 123_000_000));

        PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchant =
                new PaymentCreateCommandDTO.SubMerchantInfoDTO();
        subMerchant.setSubId("SUB-1001");
        subMerchant.setSubName("Travel Merchant");
        command.setSubMerchantInfo(subMerchant);

        PaymentCreateCommandDTO.BillingCardHolderInfoDTO billing =
                new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
        billing.setFirstName("John");
        billing.setLastName("Smith");
        billing.setEmail("john@example.com");
        billing.setPhone("+12025550124");
        billing.setCountry("USA");
        billing.setState("NY");
        billing.setCity("New York");
        billing.setStreet("100 Main Street");
        billing.setPostal("10001");
        command.setBillingCardHolderInfo(billing);

        PaymentCreateCommandDTO.PayerInfoDTO payer = new PaymentCreateCommandDTO.PayerInfoDTO();
        payer.setPayerId("CUSTOMER-1");
        payer.setEmail("payer@example.com");
        payer.setPhone("+12025550125");
        payer.setIpAddress("203.0.113.10");
        payer.setSessionId("SESSION-1");
        payer.setBrowserInfo(Map.of("browser", Map.of("name", "Chrome", "version", "128.0.0.0")));
        payer.setUserAgent("Mozilla/5.0");
        command.setPayerInfo(payer);

        PaymentCreateCommandDTO.ShippingInfoDTO shipping = new PaymentCreateCommandDTO.ShippingInfoDTO();
        shipping.setFirstName("John");
        shipping.setLastName("Smith");
        shipping.setStreet("200 Shipping Street");
        shipping.setCountry("USA");
        command.setShippingInfo(shipping);

        PaymentCreateCommandDTO.GoodsInfoDTO goods = new PaymentCreateCommandDTO.GoodsInfoDTO();
        goods.setName("Travel Booking");
        goods.setQuantity(1);
        goods.setAmount(new BigDecimal("120.00"));
        goods.setCurrency("USD");
        command.setGoodsInfo(List.of(goods));
        return command;
    }
}
