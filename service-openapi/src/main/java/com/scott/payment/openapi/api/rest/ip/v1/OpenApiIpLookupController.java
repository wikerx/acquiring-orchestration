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
     * <p><strong>查询与缓存：</strong>规范化后的 IP 转换为无符号数值，并按
     * {@code acquiring:{env}:ip:v4|v6:{ipNumber}} 读取独立 JSON String。IPv4/IPv6 类型段用于防止
     * 相同数值碰撞，也让 Redis Cluster 将不同 IP 自然分散到不同 Slot。冷查询使用本地不可变路由快照
     * 定位物理分表，再从 SLAVE 查询当前数据版本；缓存值携带路由版本，版本变化后旧值不会继续复用。
     * 命中默认缓存 6 小时，合法未命中默认缓存 2 分钟，Redis 异常时直接走同一条 SLAVE 查询链路。</p>
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
