package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.payment.OpenApiRefundApplicationService;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRefundController
 * @date : 2026-07-14 16:50
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 退款交易 V1 控制器，仅暴露退款入口并保持统一安全链路。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiRefundController {

    /**
     * 退款应用服务，负责把退款入口委托给内部支付核心。
     */
    private final OpenApiRefundApplicationService refundApplicationService;

    /**
     * 创建退款交易 V1 控制器。
     *
     * @param refundApplicationService 退款应用服务
     */
    public OpenApiRefundController(OpenApiRefundApplicationService refundApplicationService) {
        this.refundApplicationService = refundApplicationService;
    }

    /**
     * 发起退款交易。
     *
     * @param request Servlet 请求上下文，由安全链路填充和审计
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的退款请求参数
     * @return 退款交易受理响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantPaymentRequestDTO.class,
            validationGroups = {
                    ApiMerchantPaymentRequestDTO.Refund.class,
                    ApiMerchantPaymentRequestDTO.Format.class
            }
    )
    @PostMapping("/refund")
/**
 * 完成 refund 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @param encryptedData encrypted Data 输入值，含义由调用方法名称和所属业务对象限定
 * @param requestDTO 内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<PaymentCreateVO> refund(HttpServletRequest request,
                                                @RequestBody String encryptedData,
                                                ApiMerchantPaymentRequestDTO requestDTO) {
        return success(refundApplicationService.refund(encryptedData, requestDTO));
    }
}
