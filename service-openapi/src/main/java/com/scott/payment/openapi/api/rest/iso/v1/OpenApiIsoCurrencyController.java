package com.scott.payment.openapi.api.rest.iso.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.iso.OpenApiIsoDictionaryApplicationService;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
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
 * @classname : OpenApiIsoCurrencyController
 * @date : 2026-06-12 11:47
 * @email : scott_x@163.com
 * @description : openAPIISO币种 HTTP 控制器，位于 商户开放接口服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/iso/{version}")
public class OpenApiIsoCurrencyController {

    /**
     * 商户 OpenAPI ISO 字典查询服务。
     */
    private final OpenApiIsoDictionaryApplicationService isoDictionaryApplicationService;

    /**
     * 创建商户 OpenAPI ISO 币种查询控制器。
     *
     * @param isoDictionaryApplicationService 商户 OpenAPI ISO 字典应用服务
     */
    public OpenApiIsoCurrencyController(OpenApiIsoDictionaryApplicationService isoDictionaryApplicationService) {
        this.isoDictionaryApplicationService = isoDictionaryApplicationService;
    }

    /**
     * 查询系统支持的 ISO 币种。
     *
     * <p><strong>安全边界：</strong>{@link VerificationAndProcessing} 在进入 Controller 前完成
     * 商户身份校验、访问策略检查、商户维度限流、JWT 防重放、请求体解密和 DTO 校验；方法返回后，
     * 统一响应增强器只加密成功响应的 {@code data}，平台 {@code code/message} 保持统一响应格式。</p>
     *
     * <p><strong>查询与缓存：</strong>未提供 {@code alphabeticCode/numericCode} 时读取全部启用币种的
     * 常驻快照；提供一个或两个代码时，从固定代码 Hash 一次读取对应别名，两个代码必须解析为
     * 同一条币种记录，因此语义等同于精确 {@code AND} 查询。代码 Hash 未建立时从 SLAVE 全量加载
     * 一次并原子重建；Hash 已就绪但别名不存在时直接返回空列表；Redis 不可用时使用全部已提供条件
     * 执行一次 SLAVE 精确查询。管理端数据变更必须在从库同步完成后删除全量快照和代码 Hash。</p>
     *
     * @param request       Servlet 请求上下文，由统一安全链路读取请求头和调用方信息
     * @param encryptedData 商户加密查询参数，仅由请求解密组件处理，业务方法不直接读取
     * @param requestDTO    解密并校验后的精确查询条件；全部字段为空表示查询全部启用币种
     * @return 币种列表；未命中返回空列表，成功响应 data 由统一响应增强器加密
     */
    @VerificationAndProcessing(dataReceiver = IsoCurrencyQueryRequestDTO.class)
    @PostMapping("/currencies/query")
    public CommonResult<List<IsoCurrencyVO>> queryCurrencies(HttpServletRequest request,
                                                             @RequestBody String encryptedData,
                                                             IsoCurrencyQueryRequestDTO requestDTO) {
        return success(isoDictionaryApplicationService.queryCurrencies(requestDTO));
    }
}
