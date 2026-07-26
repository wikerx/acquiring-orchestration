package com.scott.payment.openapi.api.rest.iso.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.iso.OpenApiIsoDictionaryApplicationService;
import com.scott.payment.openapi.dto.body.iso.IsoCountryQueryRequestDTO;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoCountryController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Iso Country 管理接口，位于 service-openapi 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/iso/{version}")
public class OpenApiIsoCountryController {

    /**
     * 商户 OpenAPI ISO 字典查询服务。
     */
    private final OpenApiIsoDictionaryApplicationService isoDictionaryApplicationService;

    /**
     * 创建商户 OpenAPI ISO 国家地区查询控制器。
     *
     * @param isoDictionaryApplicationService 商户 OpenAPI ISO 字典应用服务
     */
    public OpenApiIsoCountryController(OpenApiIsoDictionaryApplicationService isoDictionaryApplicationService) {
        this.isoDictionaryApplicationService = isoDictionaryApplicationService;
    }

    /**
     * 查询系统支持的国家地区列表。
     *
     * @param request       Servlet 请求上下文
     * @param encryptedData 商户加密查询参数
     * @param requestDTO    解密后的查询条件
     * @return 国家地区列表，响应 data 会由响应增强统一加密
     */
    /**
     * 查询商户 OpenAPI列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param encryptedData 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @VerificationAndProcessing(dataReceiver = IsoCountryQueryRequestDTO.class)
    @PostMapping("/countries/query")
    public CommonResult<List<IsoCountryVO>> queryCountries(HttpServletRequest request,
                                                           @RequestBody String encryptedData,
                                                           IsoCountryQueryRequestDTO requestDTO) {
        return success(isoDictionaryApplicationService.queryCountries(requestDTO));
    }
}
