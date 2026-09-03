package com.scott.payment.openapi.application.iso;

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
 * @classname : OpenApiIsoDictionaryApplicationService
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : openAPIISOdictionary应用服务，位于 商户开放接口服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class OpenApiIsoDictionaryApplicationService {

    /**
     * 商户 OpenAPI ISO 字典查询服务。
     */
    private final OpenApiIsoDictionaryService isoDictionaryService;

    /**
     * 创建商户 OpenAPI ISO 字典应用服务。
     *
     * @param isoDictionaryService 商户 OpenAPI ISO 字典查询服务
     */
    public OpenApiIsoDictionaryApplicationService(OpenApiIsoDictionaryService isoDictionaryService) {
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 查询国家地区列表。
     *
     * @param requestDTO 商户查询条件
     * @return 国家地区响应列表
     */
    public List<IsoCountryVO> queryCountries(IsoCountryQueryRequestDTO requestDTO) {
        return isoDictionaryService.queryCountries(requestDTO);
    }

    /**
     * 查询币种列表。
     *
     * @param requestDTO 商户查询条件
     * @return 币种响应列表
     */
    public List<IsoCurrencyVO> queryCurrencies(IsoCurrencyQueryRequestDTO requestDTO) {
        return isoDictionaryService.queryCurrencies(requestDTO);
    }
}
