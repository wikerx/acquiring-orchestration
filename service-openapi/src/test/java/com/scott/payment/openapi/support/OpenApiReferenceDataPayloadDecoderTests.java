package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.dto.body.reference.CardBinLookupRequestDTO;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.PrivateKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证 IP 与 Card BIN 解密明文只接受协议声明的单个字符串字段。 */
class OpenApiReferenceDataPayloadDecoderTests {

    private final OpenApiPayloadCrypto crypto = mock(OpenApiPayloadCrypto.class);
    private final OpenApiPayloadKeyProvider keyProvider = mock(OpenApiPayloadKeyProvider.class);
    private final OpenApiPayloadDecoder decoder = new OpenApiPayloadDecoder(crypto, keyProvider);

    @Test
    void shouldDecodeDeclaredIpAndCardBinFields() {
        IpLookupRequestDTO ipRequest = (IpLookupRequestDTO) decode(
                "{\"ipAddress\":\"2001:db8::1\"}", IpLookupRequestDTO.class);
        CardBinLookupRequestDTO binRequest = (CardBinLookupRequestDTO) decode(
                "{\"cardBin\":\"4111111234567890\"}", CardBinLookupRequestDTO.class);

        assertThat(ipRequest.getIpAddress()).isEqualTo("2001:db8::1");
        assertThat(binRequest.getCardBin()).isEqualTo("4111111234567890");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ipAddress\":123}",
            "{\"ipAddress\":\"8.8.8.8\",\"unknown\":null}",
            "[]",
            "null"
    })
    void shouldRejectInvalidIpQueryShape(String json) {
        assertParameterInvalid(json, IpLookupRequestDTO.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"cardBin\":411111}",
            "{\"cardBin\":\"411111\",\"pan\":null}",
            "[]",
            "null"
    })
    void shouldRejectInvalidCardBinQueryShape(String json) {
        assertParameterInvalid(json, CardBinLookupRequestDTO.class);
    }

    private void assertParameterInvalid(String json, Class<?> receiver) {
        assertThatThrownBy(() -> decode(json, receiver))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
    }

    private Object decode(String json, Class<?> receiver) {
        OpenApiRequestHeaderDTO header = new OpenApiRequestHeaderDTO();
        header.setMerchantId("test-merchant");
        PrivateKey privateKey = mock(PrivateKey.class);
        when(keyProvider.getPlatformPrivateKey("test-merchant")).thenReturn(privateKey);
        when(crypto.decrypt("test-cipher", privateKey)).thenReturn(json);
        return decoder.decode("{\"data\":\"test-cipher\"}", receiver, header);
    }
}
