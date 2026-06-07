package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountQueryRequest
 * @date : 2026-06-06 00:00
 * @description : 管理后台用户账号查询请求
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserAccountQueryRequest extends PageRequest {

    private String loginAccount;
    private String mobile;
    private String email;
    private Integer status;
}
