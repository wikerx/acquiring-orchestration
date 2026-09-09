package com.scott.payment.job.client.clearing;

import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Request;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Response;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingInternalClient
 * @date : 2026-09-01 22:30
 * @email : scott_x@163.com
 * @description : service-job 调用清分补偿扫描的内部边界；Job 只负责触发和汇总，不直接读写清分、交易或资金表。
 * @status : update
 */
public interface ClearingInternalClient {

    /**
     * 触发 service-clearing 在指定单季度半开区间内执行一页幂等补偿扫描。
     *
     * @param request 扫描模式、时间范围、复合游标和页大小
     * @return 本页扫描、写入、跳过数量以及下一页游标
     */
    Response scan(Request request);
}
