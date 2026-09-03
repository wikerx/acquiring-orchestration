package com.scott.payment.payment.service.dto;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CapturePreparationResultDTO
 * @date : 2026-07-24 00:00
 * @email : scott_x@163.com
 * @description : Capture 本地准备结果 DTO，承载已提交的动作事实、幂等结果和渠道请求身份。
 * @status : create
 */
@Data
public class CapturePreparationResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否需要调用渠道；幂等命中、准备失败或本地终态结果均为 false。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与幂等结果和准备结果共同决定是否允许发起渠道调用。
     * </p>
     */
    private boolean callChannel;

    /**
     * 是否命中既有幂等结果；为 true 时必须复用原结果且禁止重复调用渠道。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与幂等结果和准备结果共同决定是否允许发起渠道调用。
     * </p>
     */
    private boolean duplicate;

    /**
     * 资金类请求幂等键，用于在同一商户和交易动作范围内识别重复提交。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与商户号、交易类型和原交易共同限定重复请求的唯一范围。
     * </p>
     */
    private String idempotencyKey;

    /**
     * 完成本地准备和字段归一后的支付命令，供渠道调用阶段使用。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateCommandDTO commandDTO;

    /**
     * 后续交易关联的原交易主单快照，用于校验可操作状态、剩余金额和原渠道身份。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private TransactionOrderDO sourceOrderDO;

    /**
     * 本次交易锁定的渠道路由结果，后续渠道调用不得重新选择路由。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentRouteResultDTO routeResultDTO;

    /**
     * 已完成金额、币种和渠道身份归一的渠道请求，仅用于本次渠道调用。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentPreparedChannelRequestDTO preparedChannelRequestDTO;

    /**
     * 无需调用渠道时直接返回的支付结果，例如幂等命中或准备阶段拒绝。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateResultDTO resultDTO;

    /**
     * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
     * <p>
     * 单位：位；格式：非负整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：必须等于 ISO 4217 币种精度，禁止默认按 2 位处理；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private int currencyExponent;

    /**
     * 创建命中幂等结果的准备结果，明确禁止再次调用渠道。
     * @param resultDTO 已持久化的原请款结果快照
     * @return 禁止再次调用渠道的幂等命中结果
     */
    public static CapturePreparationResultDTO duplicate(PaymentCreateResultDTO resultDTO) {
        CapturePreparationResultDTO target = new CapturePreparationResultDTO();
        target.setDuplicate(true);
        target.setCallChannel(false);
        target.setResultDTO(resultDTO);
        return target;
    }
}
