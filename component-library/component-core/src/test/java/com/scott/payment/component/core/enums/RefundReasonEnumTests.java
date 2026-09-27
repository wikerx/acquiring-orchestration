package com.scott.payment.component.core.enums;

import com.scott.payment.component.core.exception.ApiException;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundReasonEnumTests
 * @date : 2026-09-27 18:00
 * @email : scott_x@163.com
 * @description : 验证退款原因国际化、其他原因的空白与长度边界，以及旧版文本请求兼容。
 * @status : create
 */
class RefundReasonEnumTests {
    /** 中英文原因均由编码生成，旧文案不能覆盖选择的真实原因。 */
    @Test void shouldFormatReasonInRequestLocale() {
        assertThat(RefundReasonEnum.resolveRequestReason("PARTIAL_RETURN", "  退回两件  ", "其他", Locale.CHINA))
                .isEqualTo("部分商品退货 - 退回两件");
        assertThat(RefundReasonEnum.resolveRequestReason("PRICE_ADJUSTMENT", null, null, Locale.ENGLISH))
                .isEqualTo("Discount or price adjustment");
    }
    /** 其他、空白、超长及未知编码必须拒绝，普通原因说明可以留空。 */
    @Test void shouldRejectInvalidReasonAndDetails() {
        for (String blank : new String[] {null, "", " \t\n", "\u3000", "\u00a0", "\ufeff"}) {
            assertThatThrownBy(() -> RefundReasonEnum.resolveRequestReason("OTHER", blank, "Other - spoofed", Locale.ENGLISH))
                    .isInstanceOf(ApiException.class).hasMessageContaining("Provide refund details");
        }
        assertThatThrownBy(() -> RefundReasonEnum.resolveRequestReason("UNKNOWN", "test", null, Locale.CHINA))
                .isInstanceOf(ApiException.class).hasMessageContaining("退款原因无效");
        assertThatThrownBy(() -> RefundReasonEnum.resolveRequestReason("OTHER", "a".repeat(201), null, Locale.ENGLISH))
                .isInstanceOf(ApiException.class);
        assertThat(RefundReasonEnum.resolveRequestReason("OTHER", "a".repeat(200), null, Locale.ENGLISH)).hasSize(208);
        assertThat(RefundReasonEnum.resolveRequestReason("PARTIAL_RETURN", " ", null, Locale.ENGLISH))
                .isEqualTo("Partial return of goods");
    }
    /** 旧版原因原样保留，但缺少原因或仅填写其他不能绕过校验。 */
    @Test void shouldPreserveLegacyRequestsWithoutReinterpretingHistory() {
        assertThat(RefundReasonEnum.resolveRequestReason(null, null, "商品或服务已完成", Locale.ENGLISH))
                .isEqualTo("商品或服务已完成");
        for (String invalid : new String[] {null, "", " ", "Other", "其他 - "}) {
            assertThatThrownBy(() -> RefundReasonEnum.resolveRequestReason(null, null, invalid, Locale.CHINA))
                    .isInstanceOf(ApiException.class);
        }
    }
}
