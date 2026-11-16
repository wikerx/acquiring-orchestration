package com.scott.payment.admin.support.risk;

import com.scott.payment.admin.dto.risk.RiskDTOs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RiskListValueNormalizerTest {

    private final RiskListValueNormalizer normalizer =
            new RiskListValueNormalizer(mock(RiskSensitiveValueCrypto.class));

    @Test
    void shouldExpandEightDigitBinToElevenDigitRange() {
        RiskDTOs.RiskListSaveRequest request = new RiskDTOs.RiskListSaveRequest();
        request.setMatchValueStart("34467642");
        request.setMatchValueEnd("34467642");

        RiskListValueNormalizer.NormalizedValue normalized = normalizer.normalize(
                RiskFunctionDefinition.require("BLACK", "cardBin"), request);

        assertThat(normalized.matchValueStart()).isEqualTo("34467642000");
        assertThat(normalized.matchValueEnd()).isEqualTo("34467642999");
        assertThat(normalized.matchValueMasked()).isEqualTo("34467642000-34467642999");
    }

    @Test
    void shouldKeepExactElevenDigitBinAsSingleValue() {
        RiskDTOs.RiskListSaveRequest request = new RiskDTOs.RiskListSaveRequest();
        request.setMatchValueStart("34467642123");
        request.setMatchValueEnd("34467642123");

        RiskListValueNormalizer.NormalizedValue normalized = normalizer.normalize(
                RiskFunctionDefinition.require("BLACK", "cardBin"), request);

        assertThat(normalized.matchValueStart()).isEqualTo("34467642123");
        assertThat(normalized.matchValueEnd()).isEqualTo("34467642123");
        assertThat(normalized.matchValueMasked()).isEqualTo("34467642123");
    }
}
