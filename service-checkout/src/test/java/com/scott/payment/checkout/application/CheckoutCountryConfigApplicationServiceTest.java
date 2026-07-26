package com.scott.payment.checkout.application;

import com.scott.payment.checkout.dto.CheckoutCountryConfigResponse;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCountryConfigApplicationServiceTest
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : CheckoutCountryConfigApplicationServiceTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 收银台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class CheckoutCountryConfigApplicationServiceTest {

    /**
     * 验证 ISO 国家地区字典可转换为收银台国家下拉配置。
     */
    @Test
    void shouldMapIsoCountriesToCheckoutCountryConfig() {
        IsoDictionaryService isoDictionaryService = mock(IsoDictionaryService.class);
        when(isoDictionaryService.listCountries()).thenReturn(List.of(
                country("US", "United States", "美国", "🇺🇸", "en"),
                country("HK", "Hong Kong", "香港", "🇭🇰", "zh")
        ));
        CheckoutCountryConfigApplicationService service = new CheckoutCountryConfigApplicationService(isoDictionaryService);

        List<CheckoutCountryConfigResponse> countries = service.listCountries();

        assertThat(countries).hasSize(2);
        assertThat(countries.get(0).countryCode()).isEqualTo("HK");
        assertThat(countries.get(0).defaultLanguage()).isEqualTo("zh-CN");
        assertThat(countries.get(0).flagIconUrl()).startsWith("data:image/svg+xml,");
        assertThat(countries.get(0).supportedLanguages()).containsExactly("en-US", "zh-CN");
        assertThat(countries.get(1).countryCode()).isEqualTo("US");
        assertThat(countries.get(1).defaultLanguage()).isEqualTo("en-US");
    }

    /**
     * 验证 ISO 字典没有国旗时不伪造图片地址，由前端显示默认地球图标。
     */
    @Test
    void shouldLeaveFlagIconUrlEmptyWhenFlagEmojiMissing() {
        IsoDictionaryService isoDictionaryService = mock(IsoDictionaryService.class);
        when(isoDictionaryService.listCountries()).thenReturn(List.of(
                country("ZZ", "Unknown Region", "未知地区", "", "")
        ));
        CheckoutCountryConfigApplicationService service = new CheckoutCountryConfigApplicationService(isoDictionaryService);

        List<CheckoutCountryConfigResponse> countries = service.listCountries();

        assertThat(countries).hasSize(1);
        assertThat(countries.get(0).countryCode()).isEqualTo("ZZ");
        assertThat(countries.get(0).flagIconUrl()).isEmpty();
        assertThat(countries.get(0).defaultLanguage()).isEqualTo("en-US");
    }

    /**
     * 构造 ISO 国家地区测试数据。
     *
     * @param alpha2              alpha-2 国家地区代码
     * @param shortEnglishName    英文简称
     * @param chineseName         中文名称
     * @param flagEmoji           国旗 Emoji
     * @param primaryLanguageCode 主要语言代码
     * @return ISO 国家地区信息
     */
    private IsoCountryInfo country(String alpha2,
                                   String shortEnglishName,
                                   String chineseName,
                                   String flagEmoji,
                                   String primaryLanguageCode) {
        return new IsoCountryInfo(
                alpha2,
                alpha2 + "A",
                "000",
                shortEnglishName,
                shortEnglishName,
                chineseName,
                "AS",
                "亚洲",
                flagEmoji,
                primaryLanguageCode,
                "",
                "",
                "USD"
        );
    }
}
