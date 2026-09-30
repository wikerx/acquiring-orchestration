package com.scott.payment.openapi.converter;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoDictionaryConverter
 * @date : 2026-09-30 00:00
 * @email : scott_x@163.com
 * @description : 将 ISO 公共字典信息映射为商户 OpenAPI 响应，保留可选字段的 null 值。
 * @status : create
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OpenApiIsoDictionaryConverter {

    OpenApiIsoDictionaryConverter INSTANCE = Mappers.getMapper(OpenApiIsoDictionaryConverter.class);

    /**
     * 转换国家地区信息。
     *
     * @param countryInfo ISO 国家地区信息，可为空
     * @return 商户响应对象，源对象为空时返回 null
     */
    IsoCountryVO toCountryVO(IsoCountryInfo countryInfo);

    /**
     * 转换币种信息。
     *
     * @param currencyInfo ISO 币种信息，可为空
     * @return 商户响应对象，源对象为空时返回 null
     */
    IsoCurrencyVO toCurrencyVO(IsoCurrencyInfo currencyInfo);
}
