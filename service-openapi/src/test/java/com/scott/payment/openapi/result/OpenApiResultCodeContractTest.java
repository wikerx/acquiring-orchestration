package com.scott.payment.openapi.result;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户 OpenAPI V1.3 五位返回码的发布契约测试。
 *
 * <p>测试阻止重复编码、非法格式、漏项以及请求结果与交易结果混层进入对外协议。</p>
 */
class OpenApiResultCodeContractTest {

    /** V1.3 已发布的完整编码基线；任何新增、删除或替换都必须先完成协议评审。 */
    private static final Set<String> V1_3_PUBLISHED_CODES = Set.of(
            "A0000", "A0001", "A0002",
            "A1001", "A1002", "A1003", "A1004", "A1005", "A1006", "A1007", "A1008",
            "A2001", "A2002", "A2003", "A2004", "A2005", "A2006",
            "A3001", "A3002", "A3003", "A4001",
            "C1001", "C1002", "C1003", "C1004", "C2001", "C2002",
            "T0000", "T0001", "T0002", "T0003",
            "T1001", "T1002", "T1003", "T1004", "T1005",
            "T2001", "T2002", "T2003", "T3001", "T4001", "T4002",
            "T5001", "T5002", "T5003",
            "F1000", "F2000", "F3000");

    /**
     * 校验 V1.3 发布的 48 个返回码均为唯一的五位 A/C/T/F 编码。
     */
    @Test
    void shouldPublishExactV13CodeSetWithUniqueFiveCharacterCodes() {
        var codes = Arrays.stream(OpenApiResultCode.values())
                .map(OpenApiResultCode::getCode)
                .toList();

        assertThat(codes).hasSize(V1_3_PUBLISHED_CODES.size()).doesNotHaveDuplicates();
        assertThat(codes).allMatch(code -> code.matches("^[ACTF][0-9]{4}$"));
        assertThat(codes).containsExactlyInAnyOrderElementsOf(V1_3_PUBLISHED_CODES);
    }

    /** 每个公开码必须同时具备稳定键、类别、分组和处理策略，避免客户端从码前缀猜测重试。 */
    @Test
    void shouldPublishRequiredGovernanceMetadata() {
        assertThat(OpenApiResultCode.values()).allSatisfy(code -> {
            assertThat(code.canonicalKey()).isEqualTo(code.name());
            assertThat(code.category().name()).isEqualTo(code.getCode().substring(0, 1));
            assertThat(code.group()).isEqualTo(Character.digit(code.getCode().charAt(1), 10));
            assertThat(code.handlingPolicy()).isNotNull();
        });
    }

    /**
     * 已形成业务响应时，外层只表达请求已正常处理，不把交易成功与请求成功混为一层。
     */
    @Test
    void shouldBuildRequestSuccessEnvelopeWithA0000() {
        Object businessData = new Object();

        CommonResult<Object> result = OpenApiResponseFactory.success(businessData);

        assertThat(result.getCode()).isEqualTo("A0000");
        assertThat(result.getMessage()).isEqualTo("Request processed successfully.");
        assertThat(result.getData()).isSameAs(businessData);
    }

    /** 对外工厂只允许选择可信受众，商户与付款人文案必须隔离且统一为英文。 */
    @Test
    void shouldPublishAudienceSpecificEnglishMessages() {
        CommonResult<Void> merchant = OpenApiResponseFactory.error(OpenApiResultCode.SYSTEM_ERROR);
        CommonResult<Void> payer = OpenApiResponseFactory.error(
                OpenApiResultCode.SYSTEM_ERROR, OpenApiMessageAudience.PAYER);

        assertThat(merchant.getMessage())
                .isNotEqualTo(payer.getMessage())
                .doesNotContainPattern("[\\p{IsHan}]");
        assertThat(payer.getMessage()).doesNotContainPattern("[\\p{IsHan}]");
    }
}
