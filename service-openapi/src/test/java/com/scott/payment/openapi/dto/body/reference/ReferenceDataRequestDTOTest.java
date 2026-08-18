package com.scott.payment.openapi.dto.body.reference;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReferenceDataRequestDTOTest
 * @date : 2026-08-11 15:47
 * @email : scott_x@163.com
 * @description : 商户基础数据检索请求校验测试，阻止空 IP、非法 BIN 和完整卡号进入查询层
 * @status : create
 */
@Slf4j
class ReferenceDataRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 校验卡 BIN 只接受 6 至 11 位纯数字。
     */
    @Test
    void shouldRestrictCardBinToSixThroughElevenDigits() {
        assertThat(violationsForCardBin("411111")).isZero();
        assertThat(violationsForCardBin("41111112345")).isZero();
        assertThat(violationsForCardBin("41111")).isPositive();
        assertThat(violationsForCardBin("411111123456")).isPositive();
        assertThat(violationsForCardBin("41111A")).isPositive();
        log.info("卡 BIN 长度和纯数字校验完成，允许范围: 6-11");
    }

    /**
     * 校验 IP 请求不能为空且不能超过标准 IPv6 文本长度。
     */
    @Test
    void shouldRejectBlankOrOversizedIpInput() {
        IpLookupRequestDTO blankRequest = new IpLookupRequestDTO();
        blankRequest.setIpAddress(" ");
        IpLookupRequestDTO oversizedRequest = new IpLookupRequestDTO();
        oversizedRequest.setIpAddress("1".repeat(46));

        assertThat(validator.validate(blankRequest)).isNotEmpty();
        assertThat(validator.validate(oversizedRequest)).isNotEmpty();
        log.info("IP 请求空值和最大长度校验完成，最大长度: 45");
    }

    private int violationsForCardBin(String cardBin) {
        CardBinLookupRequestDTO request = new CardBinLookupRequestDTO();
        request.setCardBin(cardBin);
        return validator.validate(request).size();
    }
}
