package com.scott.payment.openapi.api.rest.cardbin.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.reference.OpenApiReferenceDataApplicationService;
import com.scott.payment.openapi.dto.body.reference.CardBinLookupRequestDTO;
import com.scott.payment.openapi.vo.reference.CardBinLookupVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiCardBinLookupController
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 卡 BIN 归属检索入口；安全链路负责加解密和防重放，长输入仅用前 11 位查询。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/card-bin/{version}")
public class OpenApiCardBinLookupController {

    /** 商户基础数据检索应用服务，不允许为空。 */
    private final OpenApiReferenceDataApplicationService applicationService;

    /**
     * 创建商户卡 BIN 归属检索控制器。
     *
     * @param applicationService 基础数据检索应用服务
     */
    public OpenApiCardBinLookupController(OpenApiReferenceDataApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询卡 BIN 的品牌、类型、发卡行及国家归属。
     *
     * <p><strong>安全边界：</strong>{@link VerificationAndProcessing} 在进入 Controller 前完成
     * 商户身份校验、访问策略检查、商户维度限流、JWT 防重放、请求体解密和 DTO 校验。输入必须为
     * 至少 6 位纯数字；完整卡号仅用于提取查询前缀，不得写入日志、缓存或响应。</p>
     *
     * <p><strong>查询与缓存：</strong>OpenAPI 与支付侧共用同一个查询组件：6 至 10 位右补零到 11 位，
     * 超过 11 位只取前 11 位；优先查询由数据库有效区间构建的有界 Redis 分片索引，并按最长 BIN
     * 精度及数据库来源优先级返回结果。完整分片中的未命中是权威空结果，不创建商户输入对应的永久
     * Redis Key；仅在索引发布中、不可用或损坏时从 SLAVE 执行一次 SQL。数据库未命中时使用统一
     * 卡品牌规则兜底，但 {@code matched} 保持 false，避免把推断结果当成数据库归属事实。</p>
     *
     * @param request       Servlet 请求上下文，由统一安全链路读取请求头和调用方信息
     * @param encryptedData 商户加密请求体，仅由请求解密组件处理，业务方法不直接读取
     * @param requestDTO    解密并校验后的卡 BIN 或卡号数字串
     * @return 卡 BIN 归属结果；成功响应不回显完整卡号，data 由统一响应增强器加密
     */
    @VerificationAndProcessing(dataReceiver = CardBinLookupRequestDTO.class)
    @PostMapping("/query")
    public CommonResult<CardBinLookupVO> queryCardBin(HttpServletRequest request,
                                                       @RequestBody String encryptedData,
                                                       CardBinLookupRequestDTO requestDTO) {
        return success(applicationService.queryCardBin(requestDTO));
    }
}
