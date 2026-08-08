package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundSearchResponse;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRefundQueryService
 * @date : 2026-08-08 00:10
 * @email : scott_x@163.com
 * @description : 管理端退款只读查询服务，在 service-admin 内完成退款列表、统计和详情读取，不承载审批等资金状态变更。
 * @status : create
 */
public interface AdminRefundQueryService {

    /**
     * 查询退款与撤销分页及当前筛选条件下的统计结果。
     *
     * @param query 管理端退款筛选、时间范围和分页条件
     * @return 退款分页和统计结果
     */
    RefundSearchResponse search(RefundQuery query);

    /**
     * 使用交易号和真实分片时间查询退款详情。
     *
     * @param transactionId 退款或撤销交易号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 退款记录和交易生命周期详情
     */
    RefundDetailResponse detail(String transactionId, LocalDateTime transactionDateTime);
}
