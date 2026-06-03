package com.scott.payment.openapi.api.rest.iso.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.body.iso.IsoCountryQueryRequestDTO;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.service.OpenApiIsoDictionaryService;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoDictionaryController
 * @date : 2026-06-03 15:18
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI ISO 国家地区与币种查询控制器
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/iso/{version}")
public class OpenApiIsoDictionaryController {

    /**
     * 商户 OpenAPI ISO 字典查询服务。
     */
    private final OpenApiIsoDictionaryService isoDictionaryService;

    /**
     * 创建商户 OpenAPI ISO 字典查询控制器。
     *
     * @param isoDictionaryService 商户 OpenAPI ISO 字典查询服务
     */
    public OpenApiIsoDictionaryController(OpenApiIsoDictionaryService isoDictionaryService) {
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 查询系统支持的国家地区列表。
     * <p>
     * 商户请求体仍需按 OpenAPI 标准加密。明文 JSON 可为 `{}` 查询全部，也可传 alpha2、alpha3、numeric、
     * continentCode、currencyAlpha3Code 等标准字段组合过滤。
     *
     * @param request       Servlet 请求上下文
     * @param encryptedData 商户密文请求体
     * @param requestDTO    解密后的查询条件
     * @return 国家地区列表，响应 data 会由响应增强统一加密
     */
    @VerificationAndProcessing(dataReceiver = IsoCountryQueryRequestDTO.class)
    @PostMapping("/countries")
    public CommonResult<List<IsoCountryVO>> queryCountries(HttpServletRequest request,
                                                           @RequestBody String encryptedData,
                                                           IsoCountryQueryRequestDTO requestDTO) {
        return CommonResult.success(isoDictionaryService.queryCountries(requestDTO));
    }

    /**
     * 查询系统支持的币种列表。
     * <p>
     * 商户请求体仍需按 OpenAPI 标准加密。明文 JSON 可为 `{}` 查询全部，也可传 alphabeticCode、numericCode、
     * englishName、chineseName、currencySymbol 等标准字段组合过滤。
     *
     * @param request       Servlet 请求上下文
     * @param encryptedData 商户密文请求体
     * @param requestDTO    解密后的查询条件
     * @return 币种列表，响应 data 会由响应增强统一加密
     */
    @VerificationAndProcessing(dataReceiver = IsoCurrencyQueryRequestDTO.class)
    @PostMapping("/currencies")
    public CommonResult<List<IsoCurrencyVO>> queryCurrencies(HttpServletRequest request,
                                                             @RequestBody String encryptedData,
                                                             IsoCurrencyQueryRequestDTO requestDTO) {
        return CommonResult.success(isoDictionaryService.queryCurrencies(requestDTO));
    }
}
