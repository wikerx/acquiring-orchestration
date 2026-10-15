package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.db.reference.cache.CardBinCacheReader;
import com.scott.payment.component.db.reference.cache.IpLookupCacheReader;
import com.scott.payment.component.db.reference.model.CardBinLookupCacheEntry;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataCacheReaderTest
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : OpenAPI IP 和 Card BIN 对公共缓存读取器的适配行为测试
 * @status : update
 */
class OpenApiReferenceDataCacheReaderTest {

    @Test
    void shouldDelegateIpLookupToSharedReader() {
        IpLookupCacheReader ipReader = mock(IpLookupCacheReader.class);
        CardBinCacheReader binReader = mock(CardBinCacheReader.class);
        IpLookupResult expected = new IpLookupResult(true, "8.8.8.8", "IPV4",
                "US", "USA", "840", "United States", "California", "Mountain View");
        when(ipReader.lookup("8.8.8.8")).thenReturn(expected);
        OpenApiReferenceDataCacheReader reader = new OpenApiReferenceDataCacheReader(ipReader, binReader);

        assertThat(reader.lookupIp("8.8.8.8")).isSameAs(expected);
        verify(ipReader).lookup("8.8.8.8");
    }

    @Test
    void shouldUseSharedElevenDigitCardBinLookupAndPreserveMerchantInput() {
        IpLookupCacheReader ipReader = mock(IpLookupCacheReader.class);
        CardBinCacheReader binReader = mock(CardBinCacheReader.class);
        when(binReader.findByPrefix("51234500000"))
                .thenReturn(entry(hit("51234500000", "MASTERCARD")));
        OpenApiReferenceDataCacheReader reader = new OpenApiReferenceDataCacheReader(ipReader, binReader);

        CardBinLookupResult result = reader.lookupCardBin("512345");

        assertThat(result.matched()).isTrue();
        assertThat(result.cardBin()).isEqualTo("512345");
        assertThat(result.cardBrand()).isEqualTo("MASTERCARD");
        verify(binReader).findByPrefix("51234500000");
    }

    @Test
    void shouldTruncateLongCardInputBeforeSharedLookupAndResponse() {
        IpLookupCacheReader ipReader = mock(IpLookupCacheReader.class);
        CardBinCacheReader binReader = mock(CardBinCacheReader.class);
        when(binReader.findByPrefix("41111112345"))
                .thenReturn(entry(hit("41111112345", "VISA")));
        OpenApiReferenceDataCacheReader reader = new OpenApiReferenceDataCacheReader(ipReader, binReader);

        CardBinLookupResult result = reader.lookupCardBin("4111111234567890");

        assertThat(result.cardBin()).isEqualTo("41111112345");
        verify(binReader).findByPrefix("41111112345");
    }

    @Test
    void shouldApplyExistingBrandFallbackWithoutChangingMissState() {
        IpLookupCacheReader ipReader = mock(IpLookupCacheReader.class);
        CardBinCacheReader binReader = mock(CardBinCacheReader.class);
        when(binReader.findByPrefix("41111100000"))
                .thenReturn(entry(CardBinLookupResult.miss("41111100000")));
        OpenApiReferenceDataCacheReader reader = new OpenApiReferenceDataCacheReader(ipReader, binReader);

        CardBinLookupResult result = reader.lookupCardBin("411111");

        assertThat(result.matched()).isFalse();
        assertThat(result.cardBrand()).isEqualTo("VISA");
        assertThat(result.issuerBank()).isNull();
    }

    @Test
    void shouldRejectInvalidCardBinBeforeSharedCacheAccess() {
        IpLookupCacheReader ipReader = mock(IpLookupCacheReader.class);
        CardBinCacheReader binReader = mock(CardBinCacheReader.class);
        OpenApiReferenceDataCacheReader reader = new OpenApiReferenceDataCacheReader(ipReader, binReader);

        assertThatThrownBy(() -> reader.lookupCardBin("41111"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.lookupCardBin("41111A"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(binReader, never()).findByPrefix(org.mockito.ArgumentMatchers.anyString());
    }

    private CardBinLookupCacheEntry entry(CardBinLookupResult result) {
        return new CardBinLookupCacheEntry(
                "range-index-v1", CardBinLookupCacheEntry.FORMAT_VERSION, result, null);
    }

    private CardBinLookupResult hit(String cardBin, String brand) {
        return new CardBinLookupResult(true, cardBin, 6, brand, null,
                "CREDIT", null, null, null, null, null, "Example Bank");
    }
}
