package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.openapi.converter.OpenApiReferenceDataConverterImpl;
import com.scott.payment.openapi.dto.body.reference.CardBinLookupRequestDTO;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import com.scott.payment.openapi.vo.reference.CardBinLookupVO;
import com.scott.payment.openapi.vo.reference.IpLookupVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataServiceImplTest
 * @date : 2026-08-11 15:47
 * @email : scott_x@163.com
 * @description : 商户基础数据服务行为测试，校验最小响应映射和外部参数错误码
 * @status : create
 */
@Slf4j
class OpenApiReferenceDataServiceImplTest {

    /**
     * 校验公共从库命中结果被转换为稳定的商户响应字段。
     */
    @Test
    void shouldMapReferenceDataResultsForMerchantResponse() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        when(lookupService.lookupIp("8.8.8.8")).thenReturn(new IpLookupResult(
                true, "8.8.8.8", "IPV4", "US", "USA", "840",
                "United States", "California", "Mountain View"));
        when(lookupService.lookupCardBin("411111")).thenReturn(new CardBinLookupResult(
                true, "411111", 6, "VISA", "CLASSIC", "CREDIT", "GOLD",
                "United States", "US", "USA", "840", "Example Bank"));
        OpenApiReferenceDataServiceImpl service = new OpenApiReferenceDataServiceImpl(
                lookupService, new OpenApiReferenceDataConverterImpl());
        IpLookupRequestDTO ipRequest = new IpLookupRequestDTO();
        ipRequest.setIpAddress("8.8.8.8");
        CardBinLookupRequestDTO cardBinRequest = new CardBinLookupRequestDTO();
        cardBinRequest.setCardBin("411111");

        IpLookupVO ipResponse = service.queryIp(ipRequest);
        CardBinLookupVO cardBinResponse = service.queryCardBin(cardBinRequest);

        assertThat(ipResponse.getMatched()).isTrue();
        assertThat(ipResponse.getCountryAlpha3()).isEqualTo("USA");
        assertThat(cardBinResponse.getMatched()).isTrue();
        assertThat(cardBinResponse.getCardBrand()).isEqualTo("VISA");
        assertThat(cardBinResponse.getIssuerBank()).isEqualTo("Example Bank");
        log.info("基础数据响应映射校验完成，ipMatched: {}，cardBinMatched: {}",
                ipResponse.getMatched(), cardBinResponse.getMatched());
    }

    /**
     * 校验精确 IP 语法错误转换为统一商户参数错误，不泄露底层解析信息。
     */
    @Test
    void shouldMapInvalidIpLiteralToOpenApiParameterError() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        when(lookupService.lookupIp("example.com"))
                .thenThrow(new IllegalArgumentException("internal parser detail"));
        OpenApiReferenceDataServiceImpl service = new OpenApiReferenceDataServiceImpl(
                lookupService, new OpenApiReferenceDataConverterImpl());
        IpLookupRequestDTO request = new IpLookupRequestDTO();
        request.setIpAddress("example.com");

        assertThatThrownBy(() -> service.queryIp(request))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> {
                            assertThat(exception.getCode()).isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
                            assertThat(exception.getMessage()).doesNotContain("internal parser detail");
                        });
        log.info("非法 IP 参数错误映射校验完成，预期错误码: {}", ApiResultEnum.PARAM_INVALID.getCode());
    }
}
