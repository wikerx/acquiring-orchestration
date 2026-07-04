package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMerchantDeptDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Merchant Dept 数据库实体，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_merchant_dept")
public class SysMerchantDeptDO {

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private String merchantId;
    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long parentId;
    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String deptCode;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String deptName;
    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long leaderAccountId;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String phone;
    /**
     * 系统管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private String email;
    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private Integer sortNo;
    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private Integer status;
    /**
     * 系统管理备注字段，用于记录人工说明，不参与核心状态流转。
     */
    private String remark;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private LocalDateTime createdAt;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Long createdBy;
    /**
     * 系统管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime updatedAt;
    /**
     * 系统管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private Long updatedBy;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Long deleted;
}
