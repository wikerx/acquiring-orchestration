package com.scott.payment.openapi.api.rest.ip.v1;

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
     * 查询单个 IPv4 或 IPv6 字面量的归属信息。
     *
     * <p><strong>安全边界：</strong>{@link VerificationAndProcessing} 在进入 Controller 前完成
     * 商户身份校验、访问策略检查、商户维度限流、JWT 防重放、请求体解密和 DTO 校验；域名、CIDR
     * 和无法规范化的地址不会进入缓存或数据库。方法返回后由统一响应增强器加密成功响应的
     * {@code data}。</p>
     *
     * <p><strong>查询与缓存：</strong>规范化后的 IP 使用摘要作为固定版本命名空间内的 Hash 字段，
     * 单次 Redis Lua 同时完成过期清理和命中/未命中读取，并通过容量上限防止随机 IP 造成无界增长。
     * 冷查询使用当前不可变路由快照定位物理分表，再从 SLAVE 执行一次区间查询；缓存值绑定路由版本，
     * 路由切换后旧版本不会被继续复用。命中与合法未命中使用不同逻辑 TTL，Redis 异常时直接走同一条
     * SLAVE 查询链路。</p>
     *
     * @param request       Servlet 请求上下文，由统一安全链路读取请求头和调用方信息
     * @param encryptedData 商户加密请求体，仅由请求解密组件处理，业务方法不直接读取
     * @param requestDTO    解密并校验后的请求，只允许单个 IPv4 或 IPv6 字面量
     * @return IP 归属结果；合法未命中返回 matched=false，成功响应 data 统一加密
     */
    @VerificationAndProcessing(dataReceiver = IpLookupRequestDTO.class)
    @PostMapping("/query")
    public CommonResult<IpLookupVO> queryIp(HttpServletRequest request,
                                            @RequestBody String encryptedData,
                                            IpLookupRequestDTO requestDTO) {
        return success(applicationService.queryIp(requestDTO));
    }
}
