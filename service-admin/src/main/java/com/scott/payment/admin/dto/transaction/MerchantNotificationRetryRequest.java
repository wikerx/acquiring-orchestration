package com.scott.payment.admin.dto.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryRequest
 * @date : 2026-08-04 13:45
 * @email : scott_x@163.com
 * @description : 管理后台人工重发商户终态回调请求，强制携带页面查询得到的真实交易分片时间
 * @status : create
 */
@Data
public class MerchantNotificationRetryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 平台交易 ID，不允许为空。 */
    @NotBlank(message = "transactionId is required")
    @Size(max = 64, message = "transactionId length must not exceed 64")
    private String transactionId;

    /** 交易业务时间，用于精确定位季度分表，不允许从交易号解析。 */
    @NotNull(message = "transactionDateTime is required")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /** 管理端单次点击请求号；为空时由后端生成，允许客户端重试同一 HTTP 请求时复用。 */
    @Size(max = 48, message = "requestId length must not exceed 48")
    @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "requestId format does not match")
    private String requestId;
}
