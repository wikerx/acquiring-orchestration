package com.scott.payment.admin.annotation;

import com.scott.payment.admin.constant.AdminOperationTypeConstants;
import com.scott.payment.admin.constant.AdminOperatorTypeConstants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperationLog
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台操作日志采集注解
 * @status : create
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOperationLog {

    /**
     * 操作模块名称，例如系统配置、数据字典、操作日志。
     *
     * @return 操作模块名称
     */
    String moduleName();

    /**
     * 操作业务类型，取值参考 {@link AdminOperationTypeConstants}。
     *
     * @return 操作业务类型
     */
    int businessType() default AdminOperationTypeConstants.QUERY;

    /**
     * 操作描述，用于辅助排查日志。
     *
     * @return 操作描述
     */
    String operation();

    /**
     * 默认操作人类型，取值参考 {@link AdminOperatorTypeConstants}。
     *
     * @return 默认操作人类型
     */
    int operatorType() default AdminOperatorTypeConstants.ADMIN_USER;

    /**
     * 是否记录请求参数。请求参数会在写库前进行脱敏和长度限制。
     *
     * @return true 表示记录请求参数
     */
    boolean recordRequest() default true;

    /**
     * 是否记录响应结果。响应结果会在写库前进行脱敏和长度限制。
     *
     * @return true 表示记录响应结果
     */
    boolean recordResponse() default false;
}
