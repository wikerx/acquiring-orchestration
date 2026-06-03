package com.scott.payment.openapi.service;

import com.scott.payment.openapi.dto.body.iso.IsoCountryQueryRequestDTO;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoDictionaryService
 * @date : 2026-06-03 15:12
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI ISO 国家地区与币种查询服务
 * @status : create
 */
public interface OpenApiIsoDictionaryService {

    /**
     * 查询国家地区列表。
     *
     * @param requestDTO 商户查询条件；全部字段为空时返回全部启用国家地区
     * @return 国家地区响应列表
     */
    List<IsoCountryVO> queryCountries(IsoCountryQueryRequestDTO requestDTO);

    /**
     * 查询币种列表。
     *
     * @param requestDTO 商户查询条件；keyword 为空时返回全部启用币种
     * @return 币种响应列表
     */
    List<IsoCurrencyVO> queryCurrencies(IsoCurrencyQueryRequestDTO requestDTO);
}
