package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountQueryRequest
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户账号查询请求 DTO
 * @status : create
 *
 * <p>用于后台用户分页检索，支持按登录账号、手机号、邮箱和状态过滤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserAccountQueryRequest extends PageRequest {

    private String loginAccount;
    private String mobile;
    private String email;
    private Integer status;
}
