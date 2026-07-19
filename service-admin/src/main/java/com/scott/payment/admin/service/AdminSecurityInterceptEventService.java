package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventMarkRequest;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventResponse;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSecurityInterceptEventService
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 安全拦截事件后台服务接口，位于 service-admin 服务层，负责脱敏安全事件查询和人工处理状态维护。
 * @status : create
 */
public interface AdminSecurityInterceptEventService {

    /**
     * 分页查询安全拦截事件。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SecurityInterceptEventResponse> pageEvents(SecurityInterceptEventQuery query);

    /**
     * 按查询条件列出安全拦截事件，用于导出。
     *
     * @param query 查询条件
     * @return 事件列表
     */
    List<SecurityInterceptEventResponse> listEvents(SecurityInterceptEventQuery query);

    /**
     * 查询安全拦截事件详情。
     *
     * @param id 事件主键
     * @return 事件详情
     */
    SecurityInterceptEventResponse getEvent(Long id);

    /**
     * 标记安全拦截事件处理状态。
     *
     * @param id      事件主键
     * @param request 处理请求
     * @return 更新后的事件详情
     */
    SecurityInterceptEventResponse markEvent(Long id, SecurityInterceptEventMarkRequest request);
}
