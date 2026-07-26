package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserDO
 * @date : 2026-06-06 00:09
 * @email : scott_x@163.com
 * @description : Sys User DO 持久化模型，位于 公共组件库，映射数据库记录字段，承载主键、业务标识、状态、时间和审计信息。
 * @status : create
 */
public class SysUserDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户类型：PLATFORM平台用户，MERCHANT商户用户。
     */
    private String userType;

    /**
     * 真实姓名。
     */
    private String realName;

    /**
     * 所属部门ID，用于后台用户数据权限和组织架构展示。
     */
    private Long deptId;

    /**
     * 昵称。
     */
    private String nickname;

    /**
     * 手机号。
     */
    private String mobile;

    /**
     * 邮箱。
     */
    private String email;

    /**
     * 头像地址。
     */
    private String avatarUrl;

    /**
     * 国家地区编码。
     */
    private String countryCode;

    /**
     * 用户语言。
     */
    private String language;

    /**
     * 用户时区。
     */
    private String timezone;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 创建人ID。
     */
    private Long createdBy;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 修改人ID。
     */
    private Long updatedBy;

    /**
     * 删除标识：0未删除，大于0为删除记录ID。
     */
    private Long deleted;
}
