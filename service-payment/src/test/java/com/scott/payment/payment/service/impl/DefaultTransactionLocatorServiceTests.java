package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.TransactionLocatorDO;
import com.scott.payment.payment.mapper.TransactionLocatorMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionLocatorServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Transaction locator behavior tests.
 * @status : create
 */
class DefaultTransactionLocatorServiceTests {

    @Test
    void shouldResolveMerchantFollowUpTimesWithoutExternalShardFields() {
        TransactionLocatorMapper mapper = mock(TransactionLocatorMapper.class);
        TransactionLocatorDO locator = new TransactionLocatorDO();
        locator.setMerchantId("200045");
        locator.setMerchantOrderNo("M202608010002");
        locator.setTransactionId("TX-SOURCE-001");
        locator.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 11, 30, 0, 123_000_000));
        locator.setRootTransactionDateTime(LocalDateTime.of(2026, 8, 1, 10, 30, 0, 456_000_000));
        when(mapper.selectByTransactionId("200045", "TX-SOURCE-001")).thenReturn(locator);
        DefaultTransactionLocatorService service = new DefaultTransactionLocatorService(mapper);

        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId("200045");
        command.setMerchantOrderNo("M202608010002");
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo =
                new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfo.setSourceTransactionId("TX-SOURCE-001");
        command.setTransactionInfo(transactionInfo);

        service.enrichFollowUpRoute(command);

        assertThat(transactionInfo.getSourceTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 11, 30, 0, 123_000_000));
        assertThat(transactionInfo.getRootTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 30, 0, 456_000_000));
    }

    @Test
    void shouldResolveQueryByMerchantOrderWhenTransactionInfoIsOmitted() {
        TransactionLocatorMapper mapper = mock(TransactionLocatorMapper.class);
        TransactionLocatorDO locator = new TransactionLocatorDO();
        locator.setMerchantId("200045");
        locator.setMerchantOrderNo("M202608010002");
        locator.setTransactionId("TX-ROOT-001");
        locator.setRootTransactionId("TX-ROOT-001");
        locator.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 10, 30, 0, 123_000_000));
        locator.setRootTransactionDateTime(LocalDateTime.of(2026, 8, 1, 10, 30, 0, 123_000_000));
        when(mapper.selectRootByMerchantOrder("200045", "M202608010002")).thenReturn(locator);
        DefaultTransactionLocatorService service = new DefaultTransactionLocatorService(mapper);

        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId("200045");
        command.setMerchantOrderNo("M202608010002");

        service.enrichQueryRoute(command);

        assertThat(command.getTransactionInfo()).isNotNull();
        assertThat(command.getTransactionInfo().getSourceTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 30, 0, 123_000_000));
        assertThat(command.getTransactionInfo().getRootTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 30, 0, 123_000_000));
        verify(mapper).selectRootByMerchantOrder("200045", "M202608010002");
    }
}
