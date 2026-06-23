package com.scott.payment.admin.dto.merchant;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理端商户用户查询请求。
 *
 * <p>仅用于只读查询商户系统账号，不承载密码、密钥或授权写入参数。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMerchantUserQueryRequest extends PageRequest {

    private String merchantId;
    private String merchantName;
    private String loginAccount;
    private String realName;
    private String mobile;
    private String email;
    private String roleName;
    private String deptName;
    private String postName;
    private Integer status;
    private LocalDateTime createdStartTime;
    private LocalDateTime createdEndTime;
}
