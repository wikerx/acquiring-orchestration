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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Iso Dictionary Application 服务契约，位于 service-openapi 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 查询商户 OpenAPI列表或分页数据，供页面筛选和展示使用。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 查询商户 OpenAPI列表或分页数据，供页面筛选和展示使用。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<IsoCurrencyVO> queryCurrencies(IsoCurrencyQueryRequestDTO requestDTO) {
        return isoDictionaryService.queryCurrencies(requestDTO);
    }
}
