package com.scott.payment.job.client.payment.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelMatchClientResultDTO
 * @date : 2026-07-19 22:40
 * @email : scott_x@163.com
 * @description : service-payment 渠道查询勾兑返回结果，位于 service-job 客户端 DTO 层，用于任务日志汇总本次终态确认、继续等待和查询异常数量。
 * @status : create
 */
@Data
public class PaymentChannelMatchClientResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 扫描数量。
     */
    private int scannedCount;

    /**
     * 终态确认数量。
     */
    private int matchedCount;

    /**
     * 仍待处理数量。
     */
    private int pendingCount;

    /**
     * 查询异常数量；对应交易不会因本次异常被标记为失败终态。
     */
    private int failedCount;
}
