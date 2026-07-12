package com.scott.payment.admin.dto.merchant;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserQueryRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant User Query 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMerchantUserQueryRequest extends PageRequest {

    /**
     * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private String merchantId;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String merchantName;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String loginAccount;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String realName;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String mobile;
    /**
     * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private String email;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String roleName;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String deptName;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String postName;
    /**
     * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private Integer status;
    /**
     * 商户管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime createdStartTime;
    /**
     * 商户管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime createdEndTime;
}
