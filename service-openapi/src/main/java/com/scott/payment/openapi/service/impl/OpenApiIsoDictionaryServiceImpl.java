package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.openapi.converter.OpenApiIsoDictionaryConverter;
import com.scott.payment.openapi.dto.body.iso.IsoCountryQueryRequestDTO;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.service.OpenApiIsoDictionaryService;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import org.springframework.stereotype.Service;

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
        return countryList.stream().map(OpenApiIsoDictionaryConverter.INSTANCE::toCountryVO).toList();
    }

    /**
     * 查询币种列表。
     *
     * @param requestDTO 商户查询条件；全部字段为空时返回全部启用币种
     * @return 币种响应列表
     */
    @Override
    public List<IsoCurrencyVO> queryCurrencies(IsoCurrencyQueryRequestDTO requestDTO) {
        List<IsoCurrencyInfo> currencyList = listCurrenciesByRequest(requestDTO);
        return currencyList
                .stream()
                .map(OpenApiIsoDictionaryConverter.INSTANCE::toCurrencyVO)
                .toList();
    }

    /**
     * 有代码时使用数据库精确条件查询；未提供任何代码时返回缓存的全部启用国家。
     *
     * @param requestDTO 商户国家地区查询条件
     * @return 国家地区信息列表
     */
    private List<IsoCountryInfo> listCountriesByRequest(IsoCountryQueryRequestDTO requestDTO) {
//        参数为空，直接查询完整的列表数据
        if (requestDTO == null || (requestDTO.getAlpha2() == null
                && requestDTO.getAlpha3() == null && requestDTO.getNumeric() == null)) {
            return isoDictionaryService.listCountries();
        }
        return isoDictionaryService.listCountriesByCodes(
                requestDTO.getAlpha2(), requestDTO.getAlpha3(), requestDTO.getNumeric());
    }

    /**
     * 有代码时使用数据库精确条件查询；未提供任何代码时返回缓存的全部启用币种。
     *
     * @param requestDTO 商户币种查询条件
     * @return 币种信息列表
     */
    private List<IsoCurrencyInfo> listCurrenciesByRequest(IsoCurrencyQueryRequestDTO requestDTO) {
        if (requestDTO == null || (requestDTO.getAlphabeticCode() == null
                && requestDTO.getNumericCode() == null)) {
            return isoDictionaryService.listCurrencies();
        }
        return isoDictionaryService.listCurrenciesByCodes(
                requestDTO.getAlphabeticCode(), requestDTO.getNumericCode());
    }

}
