package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.openapi.dto.body.iso.IsoCountryQueryRequestDTO;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.service.OpenApiIsoDictionaryService;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoDictionaryServiceImpl
 * @date : 2026-06-03 15:14
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI ISO 国家地区与币种查询服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoDictionaryServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Iso Dictionary Service Impl，位于 service-openapi 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class OpenApiIsoDictionaryServiceImpl implements OpenApiIsoDictionaryService {

    /**
     * ISO 基础字典公共服务，统一从 DB/Redis 查询国家地区和币种。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 创建商户 OpenAPI ISO 字典查询服务。
     *
     * @param isoDictionaryService ISO 基础字典公共服务
     */
    public OpenApiIsoDictionaryServiceImpl(IsoDictionaryService isoDictionaryService) {
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 查询国家地区列表。
     *
     * @param requestDTO 商户查询条件；全部字段为空时返回全部启用国家地区
     * @return 国家地区响应列表
     */
    /**
     * 查询商户 OpenAPI列表或分页数据，供页面筛选和展示使用。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<IsoCountryVO> queryCountries(IsoCountryQueryRequestDTO requestDTO) {
        List<IsoCountryInfo> countryList = listCountriesByRequest(requestDTO);
        return countryList.stream().map(this::toCountryVO).toList();
    }

    /**
     * 查询币种列表。
     *
     * @param requestDTO 商户查询条件；全部字段为空时返回全部启用币种
     * @return 币种响应列表
     */
    /**
     * 查询商户 OpenAPI列表或分页数据，供页面筛选和展示使用。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<IsoCurrencyVO> queryCurrencies(IsoCurrencyQueryRequestDTO requestDTO) {
        List<IsoCurrencyInfo> currencyList = listCurrenciesByRequest(requestDTO);
        return currencyList
                .stream()
                .map(this::toCurrencyVO)
                .toList();
    }

    /**
     * 根据请求条件选择国家地区查询方式。
     *
     * @param requestDTO 商户国家地区查询条件
     * @return 国家地区信息列表
     */
    private List<IsoCountryInfo> listCountriesByRequest(IsoCountryQueryRequestDTO requestDTO) {
        if (requestDTO == null) {
            return isoDictionaryService.listCountries();
        }
        return isoDictionaryService.listCountries()
                .stream()
                .filter(country -> matchesCountryRequest(country, requestDTO))
                .toList();
    }

    /**
     * 根据请求条件选择币种查询方式。
     *
     * @param requestDTO 商户币种查询条件
     * @return 币种信息列表
     */
    private List<IsoCurrencyInfo> listCurrenciesByRequest(IsoCurrencyQueryRequestDTO requestDTO) {
        if (requestDTO == null) {
            return isoDictionaryService.listCurrencies();
        }
        return isoDictionaryService.listCurrencies()
                .stream()
                .filter(currency -> matchesCurrencyRequest(currency, requestDTO))
                .toList();
    }

    /**
     * 判断国家地区是否满足商户传入的字段级查询条件。
     * <p>
     * 所有非空字段使用 AND 关系，确保商户传多个条件时返回更精确的交集。
     *
     * @param countryInfo 国家地区信息
     * @param requestDTO  商户查询条件
     * @return true 表示满足全部非空查询条件
     */
    private boolean matchesCountryRequest(IsoCountryInfo countryInfo, IsoCountryQueryRequestDTO requestDTO) {
        return matchIfPresent(requestDTO.getAlpha2(), countryInfo.alpha2(), this::equalsNormalized)
                && matchIfPresent(requestDTO.getAlpha3(), countryInfo.alpha3(), this::equalsNormalized)
                && matchIfPresent(requestDTO.getNumeric(), countryInfo.numeric(), this::equalsNormalized)
                && matchIfPresent(requestDTO.getEnglishName(), countryInfo.englishName(), this::containsNormalized)
                && matchIfPresent(requestDTO.getShortEnglishName(), countryInfo.shortEnglishName(), this::containsNormalized)
                && matchIfPresent(requestDTO.getChineseName(), countryInfo.chineseName(), this::containsNormalized)
                && matchIfPresent(requestDTO.getContinentCode(), countryInfo.continentCode(), this::equalsNormalized)
                && matchIfPresent(requestDTO.getPrimaryLanguageCode(), countryInfo.primaryLanguageCode(), this::equalsNormalized)
                && matchIfPresent(requestDTO.getCurrencyAlpha3Code(), countryInfo.currencyAlpha3Code(), this::equalsNormalized);
    }

    /**
     * 判断币种是否满足商户传入的字段级查询条件。
     * <p>
     * 所有非空字段使用 AND 关系，确保商户传多个条件时返回更精确的交集。
     *
     * @param currencyInfo 币种信息
     * @param requestDTO   商户查询条件
     * @return true 表示满足全部非空查询条件
     */
    private boolean matchesCurrencyRequest(IsoCurrencyInfo currencyInfo, IsoCurrencyQueryRequestDTO requestDTO) {
        return matchIfPresent(requestDTO.getAlphabeticCode(), currencyInfo.alphabeticCode(), this::equalsNormalized)
                && matchIfPresent(requestDTO.getNumericCode(), currencyInfo.numericCode(), this::equalsNormalized)
                && matchIfPresent(requestDTO.getEnglishName(), currencyInfo.englishName(), this::containsNormalized)
                && matchIfPresent(requestDTO.getChineseName(), currencyInfo.chineseName(), this::containsNormalized)
                && matchIfPresent(requestDTO.getCurrencySymbol(), currencyInfo.currencySymbol(), this::equalsNormalized);
    }

    /**
     * 查询值为空时放行；查询值不为空时按指定匹配器判断。
     *
     * @param requestValue 商户传入的查询值
     * @param targetValue  字典中的目标值
     * @param matcher      匹配器
     * @return true 表示条件为空或条件命中
     */
    private boolean matchIfPresent(String requestValue, String targetValue, BiPredicate<String, String> matcher) {
        if (!StringUtils.hasText(requestValue)) {
            return true;
        }
        return matcher.test(targetValue, requestValue);
    }

    /**
     * 转换核心国家地区信息为商户 OpenAPI 响应对象。
     *
     * @param countryInfo 核心国家地区信息
     * @return 商户响应国家地区对象
     */
    private IsoCountryVO toCountryVO(IsoCountryInfo countryInfo) {
        IsoCountryVO countryVO = new IsoCountryVO();
        countryVO.setAlpha2(countryInfo.alpha2());
        countryVO.setAlpha3(countryInfo.alpha3());
        countryVO.setNumeric(countryInfo.numeric());
        countryVO.setEnglishName(countryInfo.englishName());
        countryVO.setShortEnglishName(countryInfo.shortEnglishName());
        countryVO.setChineseName(countryInfo.chineseName());
        countryVO.setContinentCode(countryInfo.continentCode());
        countryVO.setContinentName(countryInfo.continentName());
        countryVO.setFlagEmoji(countryInfo.flagEmoji());
        countryVO.setPrimaryLanguageCode(countryInfo.primaryLanguageCode());
        countryVO.setPrimaryLanguageEnglish(countryInfo.primaryLanguageEnglish());
        countryVO.setPrimaryLanguageChinese(countryInfo.primaryLanguageChinese());
        countryVO.setCurrencyAlpha3Code(countryInfo.currencyAlpha3Code());
        return countryVO;
    }

    /**
     * 转换核心币种信息为商户 OpenAPI 响应对象。
     *
     * @param currencyInfo 核心币种信息
     * @return 商户响应币种对象
     */
    private IsoCurrencyVO toCurrencyVO(IsoCurrencyInfo currencyInfo) {
        IsoCurrencyVO currencyVO = new IsoCurrencyVO();
        currencyVO.setAlphabeticCode(currencyInfo.alphabeticCode());
        currencyVO.setNumericCode(currencyInfo.numericCode());
        currencyVO.setEnglishName(currencyInfo.englishName());
        currencyVO.setChineseName(currencyInfo.chineseName());
        currencyVO.setDefaultFractionDigits(currencyInfo.defaultFractionDigits());
        currencyVO.setMinorUnitMultiplier(currencyInfo.minorUnitMultiplier());
        currencyVO.setMinimumAmount(currencyInfo.minimumAmount());
        currencyVO.setCurrencySymbol(currencyInfo.currencySymbol());
        return currencyVO;
    }

    /**
     * 判断目标值和查询值标准化后是否相等。
     *
     * @param targetValue  字典中的目标值
     * @param requestValue 商户传入的查询值
     * @return true 表示相等
     */
    private boolean equalsNormalized(String targetValue, String requestValue) {
        return normalize(targetValue).equals(normalize(requestValue));
    }

    /**
     * 判断目标值标准化后是否包含查询值。
     *
     * @param targetValue  字典中的目标值
     * @param requestValue 商户传入的查询值
     * @return true 表示包含
     */
    private boolean containsNormalized(String targetValue, String requestValue) {
        return normalize(targetValue).contains(normalize(requestValue));
    }

    /**
     * 标准化商户查询文本。
     *
     * @param value 原始文本
     * @return 去空白、短横线、下划线并转大写后的文本
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .toUpperCase(Locale.ROOT);
    }
}
