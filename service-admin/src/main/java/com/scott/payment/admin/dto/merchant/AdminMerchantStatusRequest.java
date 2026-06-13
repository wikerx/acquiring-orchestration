package com.scott.payment.admin.dto.merchant;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商户状态修改请求。
 */
@Data
public class AdminMerchantStatusRequest {

    @NotNull(message = "商户状态不能为空")
    private Integer merchantStatus;
}
