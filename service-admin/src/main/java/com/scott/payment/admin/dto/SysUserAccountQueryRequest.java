package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台用户账号查询请求 DTO。
 *
 * <p>用于后台用户分页检索，支持按账号、联系方式、部门和状态过滤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserAccountQueryRequest extends PageRequest {

    private String loginAccount;
    private String mobile;
    private String email;
    private Long deptId;
    private Integer status;
}
