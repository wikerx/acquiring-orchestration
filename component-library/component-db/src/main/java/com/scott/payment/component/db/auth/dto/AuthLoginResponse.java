package com.scott.payment.component.db.auth.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthLoginResponse
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统登录响应
 * @status : create
 */
@Data
public class AuthLoginResponse implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * Bearer token 明文，前端后续请求使用该值。
     */
    private String accessToken;

    /**
     * token 类型。
     */
    private String tokenType = "Bearer";

    /**
     * 过期秒数。
     */
    private Long expiresIn;

    /**
     * token 过期时间。
     */
    private LocalDateTime expireAt;

    /**
     * 登录账号信息。
     */
    private AuthAccountDTO account;

    /**
     * 前端菜单树。
     */
    private List<AuthMenuDTO> menus = Collections.emptyList();

    /**
     * 当前账号角色编码集合。
     */
    private List<String> roles = Collections.emptyList();

    /**
     * 后端权限标识集合。
     */
    private List<String> permissions = Collections.emptyList();
}
