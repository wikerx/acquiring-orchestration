package com.scott.payment.admin.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * ISO 国家和币种基础资料的启停状态更新请求。
 *
 * <p>请求只接受平台约定的 0 和 1，同时忽略客户端新增的未知字段以保持协议向前兼容。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IsoStatusUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 启停状态，0 表示停用，1 表示启用。
     */
    @NotNull(message = "status must not be null")
    @Min(value = 0, message = "status must be 0 or 1")
    @Max(value = 1, message = "status must be 0 or 1")
    private Integer status;
}
