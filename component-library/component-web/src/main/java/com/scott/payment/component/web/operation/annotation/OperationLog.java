package com.scott.payment.component.web.operation.annotation;

import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.web.operation.constant.OperatorTypeConstants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLog
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统操作日志自动采集注解
 * @status : create
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作模块名称，例如系统配置、数据字典、商户资料。
     *
     * @return 操作模块名称
     */
    String moduleName();

    /**
     * 操作业务类型，取值参考 {@link OperationTypeConstants}。
     *
     * @return 操作业务类型
     */
    int businessType() default OperationTypeConstants.QUERY;

    /**
     * 操作描述，用于审计人员快速理解当前接口行为。
     *
     * @return 操作描述
     */
    String operation();

    /**
     * 默认操作人类型，取值参考 {@link OperatorTypeConstants}。
     *
     * @return 默认操作人类型
     */
    int operatorType() default OperatorTypeConstants.ADMIN_USER;

    /**
     * 是否记录请求参数。请求参数会在写库或上报前进行脱敏和长度限制。
     *
     * @return true 表示记录请求参数
     */
    boolean recordRequest() default true;

    /**
     * 是否记录响应结果。响应结果会在写库或上报前进行脱敏和长度限制。
     *
     * @return true 表示记录响应结果
     */
    boolean recordResponse() default false;
}
