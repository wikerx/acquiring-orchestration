package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalSearchResponse;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelMatchAbnormalQueryService
 * @date : 2026-08-08 00:10
 * @email : scott_x@163.com
 * @description : 管理端勾兑异常只读查询服务，在 service-admin 内读取案件分页、统计和详情，案件处置命令仍由 service-payment 执行。
 * @status : create
 */
public interface AdminChannelMatchAbnormalQueryService {

    /**
     * 查询勾兑异常案件分页及当前筛选条件下的状态统计。
     *
     * @param query 案件筛选、时间范围和分页条件
     * @return 案件分页和状态统计
     */
    AbnormalSearchResponse search(AbnormalQuery query);

    /**
     * 使用案件号和真实分片时间查询案件聚合详情。
     *
     * @param eventId 勾兑异常案件号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 案件记录和交易生命周期详情
     */
    AbnormalDetailResponse detail(String eventId, LocalDateTime transactionDateTime);
}
