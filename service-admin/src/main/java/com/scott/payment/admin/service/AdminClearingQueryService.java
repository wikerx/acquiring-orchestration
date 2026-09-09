package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.Summary;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchItem;
import com.scott.payment.component.core.model.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminClearingQueryService
 * @date : 2026-08-27 14:00
 * @email : scott_x@163.com
 * @description : Admin 清分只读查询边界；读取交易逻辑数据源，不承载清分状态变更和重算命令。
 * @status : create
 */
public interface AdminClearingQueryService {

    /**
     * 查询单自然季度内的清分记录标准分页。
     *
     * @param request 清分筛选、分片时间范围和页码
     * @return 按交易时间和主键倒序的分页结果
     */
    PageResult<Summary> search(SearchRequest request);

    /**
     * 按动作号和真实交易分片时间读取当前清分修订详情。
     *
     * @param transactionId 动作级交易号
     * @param transactionDateTime 真实交易分片时间
     * @return 清分汇总、交易费用明细和独立保证金明细
     */
    DetailResponse detail(String transactionId, LocalDateTime transactionDateTime);

    /**
     * 一次读取批量重算目标的当前清分状态，避免按交易逐笔查询管理库。
     *
     * @param references 动作号和真实分片时间
     * @return 当前存在的清分汇总
     */
    List<Summary> findByReferences(List<RecalculateBatchItem> references);
}
