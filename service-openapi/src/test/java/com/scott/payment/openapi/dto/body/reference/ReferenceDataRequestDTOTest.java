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
 * @description : 商户基础数据检索请求校验测试，阻止空 IP 和非法 BIN，长 BIN 由查询层截取。
 * @status : create
 */
@Slf4j
class ReferenceDataRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 校验卡 BIN 至少 6 位；更长的数字在查询层截取前 11 位。
     */
    @Test
    void shouldRestrictCardBinToSixThroughElevenDigits() {
        assertThat(violationsForCardBin("411111")).isZero();
        assertThat(violationsForCardBin("41111112345")).isZero();
        assertThat(violationsForCardBin("41111")).isPositive();
        assertThat(violationsForCardBin("411111123456")).isZero();
        assertThat(violationsForCardBin("4111111234567890")).isZero();
        assertThat(violationsForCardBin("41111A")).isPositive();
        log.info("卡 BIN 长度和纯数字校验完成，最短长度: 6");
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
