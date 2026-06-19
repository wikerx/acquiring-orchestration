package com.scott.payment.openapi.application.iso;

import com.scott.payment.openapi.dto.body.iso.IsoCountryQueryRequestDTO;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.service.OpenApiIsoDictionaryService;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商户 OpenAPI ISO 字典应用服务。
 * <p>
 * 当前负责衔接接口层与 ISO 字典业务服务，后续可在这里统一处理访问审计和查询策略编排。
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
