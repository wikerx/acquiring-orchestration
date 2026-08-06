package com.scott.payment.payment.application;

import com.scott.payment.payment.service.RefundManagementQueryService;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundDetailResponse;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundQuery;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundSearchResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundManagementApplicationService
 * @date : 2026-08-06 15:50
 * @email : scott_x@163.com
 * @description : 退款管理查询应用服务，为内部接口编排分页统计和精确分片详情查询。
 * @status : create
 */
@Service
public class RefundManagementApplicationService {

    private final RefundManagementQueryService queryService;

    /** @param queryService 退款管理查询服务 */
    public RefundManagementApplicationService(RefundManagementQueryService queryService) {
        this.queryService = queryService;
    }

    /** @param query 查询条件 @return 退款分页和统计 */
    public RefundSearchResponse search(RefundQuery query) {
        return queryService.search(query);
    }

    /** @return 单笔退款详情 */
    public RefundDetailResponse detail(String transactionId,
                                       LocalDateTime transactionDateTime,
                                       String merchantId) {
        return queryService.detail(transactionId, transactionDateTime, merchantId);
    }
}
