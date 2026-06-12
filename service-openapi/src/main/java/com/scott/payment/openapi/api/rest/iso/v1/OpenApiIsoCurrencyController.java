package com.scott.payment.openapi.api.rest.iso.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.service.OpenApiIsoDictionaryService;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoCurrencyController
 * @date : 2026-06-12 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI ISO 币种查询控制器
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/iso/{version}")
public class OpenApiIsoCurrencyController {

    /**
     * 商户 OpenAPI ISO 字典查询服务。
     */
    private final OpenApiIsoDictionaryService isoDictionaryService;

    /**
     * 创建商户 OpenAPI ISO 币种查询控制器。
     *
     * @param isoDictionaryService 商户 OpenAPI ISO 字典查询服务
     */
    public OpenApiIsoCurrencyController(OpenApiIsoDictionaryService isoDictionaryService) {
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 查询系统支持的币种列表。
     *
     * @param request       Servlet 请求上下文
     * @param encryptedData 商户加密查询参数
     * @param requestDTO    解密后的查询条件
     * @return 币种列表，响应 data 会由响应增强统一加密
     */
    @VerificationAndProcessing(dataReceiver = IsoCurrencyQueryRequestDTO.class)
    @PostMapping("/currencies/query")
    public CommonResult<List<IsoCurrencyVO>> queryCurrencies(HttpServletRequest request,
                                                             @RequestParam("data") String encryptedData,
                                                             IsoCurrencyQueryRequestDTO requestDTO) {
        return success(isoDictionaryService.queryCurrencies(requestDTO));
    }
}
