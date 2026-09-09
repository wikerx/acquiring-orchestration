package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingIdRuleResponse
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表ID规则响应模型，位于 运营后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
 * @status : create
 */
@Data
public class ShardingIdRuleResponse {

    /**
     * 响应中的运行模式，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String mode;

    /**
     * 响应中的{@code prefixFormat}，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String prefixFormat;

    /**
     * 响应中的{@code sequenceWidth}，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Integer sequenceWidth;

    /**
     * 响应中的{@code startSequence}，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long startSequence;

    /**
     * 响应中的最大序列值，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long maxSequence;

    /**
     * 响应中的当前季度，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String currentQuarter;

    /**
     * 响应中的当前季度开始值，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentQuarterStartValue;

    /**
     * 响应中的当前季度最大值，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentQuarterMaxValue;
}
