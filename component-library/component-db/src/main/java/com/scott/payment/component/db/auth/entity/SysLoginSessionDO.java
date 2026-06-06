package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysLoginSessionDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 登录会话数据库实体
 * @status : create
 */
@Data
@TableName("sys_login_session")
public class SysLoginSessionDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统应用ID。
     */
    private Long appId;

    /**
     * 账号ID。
     */
    private Long accountId;

    /**
     * 用户主体ID。
     */
    private Long userId;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * token 哈希，数据库不保存 token 明文。
     */
    private String tokenHash;

    /**
     * 登录IP。
     */
    private String loginIp;

    /**
     * User-Agent。
     */
    private String userAgent;

    /**
     * 过期时间。
     */
    private LocalDateTime expireAt;

    /**
     * 是否退出：0否，1是。
     */
    private Integer logout;

    /**
     * 退出时间。
     */
    private LocalDateTime logoutAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;
}
