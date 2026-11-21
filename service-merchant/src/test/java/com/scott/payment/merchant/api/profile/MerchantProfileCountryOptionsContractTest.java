package com.scott.payment.merchant.api.profile;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import com.scott.payment.merchant.application.profile.MerchantProfileApplicationService;
import com.scott.payment.merchant.service.MerchantProfileService;
import com.scott.payment.merchant.service.impl.MerchantProfileChangeRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** 验证商户资料国家地区接口的权限和基础数据投影契约。 */
class MerchantProfileCountryOptionsContractTest {

    /** 商户资料页面的国家地区目录路径。 */
    private static final String OPTIONS_PATH = "/merchant/info/country-options";

    /** 只接受已认证的商户与资料查看权限。 */
    @Test
    void requiresMerchantProfileViewPermission() throws Exception {
        IsoDictionaryService dictionary = mock(IsoDictionaryService.class);
        MockMvc mvc = mvc(dictionary);

        mvc.perform(get(OPTIONS_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(OPTIONS_PATH).header("Authorization", "Bearer without-profile-view"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dictionary);
    }

    /** 返回现有目录的最小字段，按三位代码排序。 */
    @Test
    void returnsOnlyCountryPresentationFromSharedDictionary() throws Exception {
        IsoDictionaryService dictionary = mock(IsoDictionaryService.class);
        when(dictionary.listCountries()).thenReturn(List.of(
                country("US", "USA", "840", "美国", "United States of America", "United States", "🇺🇸"),
                country("CN", "CHN", "156", "中国", "China", "China", "🇨🇳")));

        mvc(dictionary).perform(get(OPTIONS_PATH).header("Authorization", "Bearer profile-view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].alpha3Code").value("CHN"))
                .andExpect(jsonPath("$.data[1].alpha3Code").value("USA"))
                .andExpect(jsonPath("$.data[1].alpha2Code").value("US"))
                .andExpect(jsonPath("$.data[1].chineseName").value("美国"))
                .andExpect(jsonPath("$.data[1].englishName").value("United States of America"))
                .andExpect(jsonPath("$.data[1].shortEnglishName").value("United States"))
                .andExpect(jsonPath("$.data[1].flagEmoji").value("🇺🇸"))
                .andExpect(jsonPath("$.data[1].numeric").doesNotExist())
                .andExpect(jsonPath("$.data[1].continentCode").doesNotExist())
                .andExpect(jsonPath("$.data[1].currencyAlpha3Code").doesNotExist())
                .andExpect(jsonPath("$.data[1].merchantId").doesNotExist());
    }

    /** 启用目录为空时不返回前端或静态数据兜底。 */
    @Test
    void returnsEmptyArrayWhenDictionaryIsEmpty() throws Exception {
        IsoDictionaryService dictionary = mock(IsoDictionaryService.class);
        when(dictionary.listCountries()).thenReturn(List.of());

        mvc(dictionary).perform(get(OPTIONS_PATH).header("Authorization", "Bearer profile-view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 通过现有商户认证拦截器执行请求，模拟会话检查结果。
     *
     * @param dictionary 基础字典数据源
     * @return 模拟 HTTP 客户端
     */
    private MockMvc mvc(IsoDictionaryService dictionary) {
        MerchantProfileApplicationService applicationService = new MerchantProfileApplicationService(
                mock(MerchantProfileService.class), mock(MerchantProfileChangeRequestService.class), dictionary);
        InternalAuthInterceptor interceptor = new InternalAuthInterceptor("MERCHANT",
                (app, authorization, method, path, permission) -> {
                    assertThat(app).isEqualTo("MERCHANT");
                    assertThat(method).isEqualTo("GET");
                    assertThat(path).isEqualTo(OPTIONS_PATH);
                    assertThat(permission).isEqualTo("merchant:info:view");
                    if (!"Bearer profile-view".equals(authorization)) {
                        throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "permission denied");
                    }
                    InternalAuthAccount account = new InternalAuthAccount();
                    account.setAppCode(app);
                    account.setMerchantId("M-TEST");
                    account.setPermissions(List.of("merchant:info:view"));
                    return account;
                },
                List.of());
        return standaloneSetup(new MerchantProfileController(applicationService)).addInterceptors(interceptor).build();
    }

    /**
     * 构造带有后台元数据的 ISO 目录项，验证商户投影仅返回表单所需字段。
     *
     * @param alpha2 两位代码
     * @param alpha3 三位代码
     * @param numeric 三位数字代码
     * @param chineseName 中文名称
     * @param englishName 英文名称
     * @param shortEnglishName 英文简称
     * @param flagEmoji 旗帜
     * @return 模拟基础目录项
     */
    private IsoCountryInfo country(String alpha2, String alpha3, String numeric, String chineseName,
                                   String englishName, String shortEnglishName, String flagEmoji) {
        return new IsoCountryInfo(alpha2, alpha3, numeric, englishName, shortEnglishName, chineseName,
                "TEST", "Test region", flagEmoji, "en", "English", "英语", "USD");
    }
}
