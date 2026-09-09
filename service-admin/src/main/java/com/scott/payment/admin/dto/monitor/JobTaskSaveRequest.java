package com.scott.payment.admin.dto.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskSaveRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务保存请求对象
 * @status : create
 *
 * <p>用于新增或更新任务调度定义，承载调度模式、执行模式、超时重试和任务参数等配置。</p>
 */
@Data
public class JobTaskSaveRequest {

    /**
     * 任务编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotBlank(message = "jobCode must not be blank")
    private String jobCode;

    /**
     * 任务名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotBlank(message = "jobName must not be blank")
    private String jobName;

    /**
     * 请求中的任务分组，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotBlank(message = "jobGroup must not be blank")
    private String jobGroup;

    /**
     * {@code handlerCode}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotBlank(message = "handlerCode must not be blank")
    private String handlerCode;

    /**
     * 请求中的{@code cronExpression}，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String cronExpression;

    /**
     * 请求中的调度器模式，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotBlank(message = "schedulerMode must not be blank")
    private String schedulerMode;

    /**
     * 请求中的触发模式，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotBlank(message = "triggerMode must not be blank")
    private String triggerMode;

    /**
     * 请求中的{@code misfireStrategy}，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotBlank(message = "misfireStrategy must not be blank")
    private String misfireStrategy;

    /**
     * 远程调用超时时间，单位为秒。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @Min(value = 1, message = "timeoutSeconds must be greater than 0")
    @Max(value = 86400, message = "timeoutSeconds must be less than or equal to 86400")
    private Integer timeoutSeconds;

    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @Min(value = 0, message = "retryCount must be greater than or equal to 0")
    @Max(value = 10, message = "retryCount must be less than or equal to 10")
    private Integer retryCount;

    /**
     * 请求中的{@code retryIntervalSeconds}，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @Min(value = 1, message = "retryIntervalSeconds must be greater than 0")
    @Max(value = 86400, message = "retryIntervalSeconds must be less than or equal to 86400")
    private Integer retryIntervalSeconds;

    /**
     * 请求中的{@code allowConcurrent}，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Integer allowConcurrent;

    /**
     * 请求中的参数，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String params;

    /**
     * 状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    @NotBlank(message = "status must not be blank")
    private String status;

    /**
     * 说明，用于保存人工备注、交易说明或配置补充说明。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String description;
}
