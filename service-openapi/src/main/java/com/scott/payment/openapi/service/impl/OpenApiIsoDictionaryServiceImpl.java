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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoDictionaryServiceImpl
 * @date : 2026-06-03 15:14
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI ISO 国家地区与币种查询服务实现
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
    @Override
    public List<IsoCountryVO> queryCountries(IsoCountryQueryRequestDTO requestDTO) {
        List<IsoCountryInfo> countryList = listCountriesByRequest(requestDTO);
        return countryList.stream().map(this::toCountryVO).toList();
    }

    /**
     * 查询币种列表。
     *
     * @param requestDTO 商户查询条件；keyword 为空时返回全部启用币种
     * @return 币种响应列表
     */
    @Override
    public List<IsoCurrencyVO> queryCurrencies(IsoCurrencyQueryRequestDTO requestDTO) {
        String keyword = requestDTO == null ? null : requestDTO.getKeyword();
        return isoDictionaryService.searchCurrencies(keyword)
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
        if (StringUtils.hasText(requestDTO.getContinentCode())) {
            return isoDictionaryService.listCountriesByContinent(requestDTO.getContinentCode());
        }
        if (StringUtils.hasText(requestDTO.getCurrencyCode())) {
            return isoDictionaryService.listCountriesByCurrency(requestDTO.getCurrencyCode());
        }
        return isoDictionaryService.searchCountries(requestDTO.getKeyword());
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
}
