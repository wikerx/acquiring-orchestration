package com.scott.payment.openapi.api.rest.reference.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.reference.OpenApiReferenceDataApplicationService;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import com.scott.payment.openapi.vo.reference.IpLookupVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIpLookupController
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 归属检索入口，密文请求和成功响应由统一安全链路解密、校验和加密
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/ip/{version}")
public class OpenApiIpLookupController {

    /** 商户基础数据检索应用服务，不允许为空。 */
    private final OpenApiReferenceDataApplicationService applicationService;

    /**
     * 创建商户 IP 归属检索控制器。
     *
     * @param applicationService 基础数据检索应用服务
     */
    public OpenApiIpLookupController(OpenApiReferenceDataApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询单个 IPv4 或 IPv6 的归属信息。
     *
     * @param request       Servlet 请求上下文
     * @param encryptedData 商户加密请求体
     * @param requestDTO    解密并校验后的请求
     * @return IP 归属结果，成功响应 data 由统一响应增强器加密
     */
    @VerificationAndProcessing(dataReceiver = IpLookupRequestDTO.class)
    @PostMapping("/query")
    public CommonResult<IpLookupVO> queryIp(HttpServletRequest request,
                                            @RequestBody String encryptedData,
                                            IpLookupRequestDTO requestDTO) {
        return success(applicationService.queryIp(requestDTO));
    }
}
