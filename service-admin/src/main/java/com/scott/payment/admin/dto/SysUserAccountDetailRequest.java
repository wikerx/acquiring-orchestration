package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 后台用户详情查询请求，仅承载需要读取维护资料的账号主键
 */
@Data
public class SysUserAccountDetailRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标后台账号主键，必须为正数。
     */
    @NotNull(message = "账号ID不能为空")
    @Positive(message = "账号ID必须为正数")
    private Long accountId;
}
