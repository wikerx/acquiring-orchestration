package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanResponse;

import java.time.LocalDateTime;

/**
 * 清分补偿批量扫描边界。
 */
public interface ClearingCompensationService {

    /**
     * 扫描单季度补偿候选并按预览或执行模式逐条处置。
     *
     * @param request 半开时间窗口、主键游标、页大小和模式
     * @param now 本轮统一平台本地审计时间
     * @return 本页扫描、写入、跳过数量及稳定逐条结果
     * @throws IllegalArgumentException 模式、窗口或游标不合法时抛出
     */
    CompensationScanResponse scan(CompensationScanRequest request, LocalDateTime now);
}
