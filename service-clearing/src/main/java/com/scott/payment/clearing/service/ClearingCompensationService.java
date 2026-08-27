package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanResponse;

import java.time.LocalDateTime;

/** 清分补偿批量扫描边界。 */
public interface ClearingCompensationService {

    /** 扫描并按请求模式预览或恢复候选。 */
    CompensationScanResponse scan(CompensationScanRequest request, LocalDateTime now);
}
