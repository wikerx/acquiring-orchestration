package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelMatchResultDTO
 * @date : 2026-07-19 22:20
 * @email : scott_x@163.com
 * @description : 渠道交易查询勾兑结果，位于 service-payment 内部接口 DTO 层，用于返回定时任务本次扫描、终态确认、继续等待和查询异常数量。
 * @status : create
 */
@Data
public class TransactionChannelMatchResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 本次扫描到的待勾兑交易数。
     */
    private int scannedCount;

    /**
     * 已确认并推进终态的交易数。
     */
    private int matchedCount;

    /**
     * 渠道仍返回处理中或待回调的交易数。
     */
    private int pendingCount;

    /**
     * 查询渠道失败或调用异常数量；这类交易仍保持非终态，后续继续等待回调或下一次查询勾兑。
     */
    private int failedCount;
}
