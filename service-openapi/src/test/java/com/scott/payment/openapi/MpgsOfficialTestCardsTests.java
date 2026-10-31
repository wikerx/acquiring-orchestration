package com.scott.payment.openapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MPGS live 测试卡目录边界测试。
 */
class MpgsOfficialTestCardsTests {

    @Test
    void shouldRejectNonOfficialCardBeforeBuildingLiveRequest() {
        assertThatThrownBy(() -> MpgsOfficialTestCards.requireOfficial("MASTERCARD", "5555555555554444"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MPGS live tests only accept cards published in the official test-card catalog");
    }
}
