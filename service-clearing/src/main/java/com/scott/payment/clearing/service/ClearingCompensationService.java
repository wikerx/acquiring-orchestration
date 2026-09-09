package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanResponse;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompensationService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分补偿批量扫描边界。
 * @status : update
 */
public interface ClearingCompensationService {

    /**
     * 扫描单季度补偿候选并按预览或执行模式逐条处置。
     *
     * @param request 半开时间窗口、主键游标、页大小和模式
     * @param now 本轮统一 UTC 审计时间
     * @return 本页扫描、写入、跳过数量及稳定逐条结果
     * @throws IllegalArgumentException 模式、窗口或游标不合法时抛出
     */
    CompensationScanResponse scan(CompensationScanRequest request, LocalDateTime now);
}
