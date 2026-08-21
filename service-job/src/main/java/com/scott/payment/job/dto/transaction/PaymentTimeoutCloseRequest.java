package com.scott.payment.job.dto.transaction;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTimeoutCloseRequest
 * @date : 2026-08-20 20:40
 * @email : scott_x@163.com
 * @description : 支付超时关单任务参数，只控制单批扫描规模，不允许任务层改变支付状态规则
 * @status : create
 */
@Data
public class PaymentTimeoutCloseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单次扫描上限，允许 1 至 1000；为空或越界时由任务处理器执行保护。 */
    private Integer limit;
}
