package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysVerifyCodeDO
 * @date : 2026-06-06 00:00
 * @description : 动态验证码数据库实体
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysVerifyCodeDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Verify Code 数据库实体，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_verify_code")
public class SysVerifyCodeDO {

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long appId;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String scene;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String receiverType;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String receiver;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String codeHash;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String codeSalt;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private LocalDateTime expireAt;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer used;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private LocalDateTime usedAt;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer verifyCount;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String sendIp;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String sendChannel;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private Integer sendStatus;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String sendFailReason;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private LocalDateTime createdAt;
}
