package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelMatchRequeryCommandDTO
 * @date : 2026-08-21 00:00
 * @email : scott_x@163.com
 * @description : 单笔渠道勾兑重查命令，位于 service-payment 内部接口 DTO 层，只携带列表返回的真实交易分片时间，禁止从交易号推断分片。
 * @status : create
 */
@Data
public class TransactionChannelMatchRequeryCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 被勾兑交易的真实分片时间，必须来自交易查询结果。
     */
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;
}
