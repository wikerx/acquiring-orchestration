package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.AlertEventAcknowledgeRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertEventQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertEventResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertNotifyLogQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertNotifyLogResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleDimensionResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleDimensionSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleOptionsResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleSaveRequest;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelAlertService
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警管理服务契约，位于 service-admin 服务层，定义后台规则、事件和邮件通知日志管理边界。
 * @status : create
 */
public interface AdminChannelAlertService {

    /**
     * 分页查询渠道预警规则。
     *
     * @param query 查询条件
     * @return 规则分页结果
     */
    PageResult<ChannelAlertRuleResponse> pageRules(ChannelAlertRuleQuery query);

    /**
     * 查询渠道预警规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     */
    ChannelAlertRuleResponse getRule(Long id);

    /**
     * 创建渠道预警规则。
     *
     * @param request 保存请求
     * @return 创建后的规则
     */
    ChannelAlertRuleResponse createRule(ChannelAlertRuleSaveRequest request);

    /**
     * 批量创建同一渠道维度下的预警规则。
     *
     * @param request 批量保存请求
     * @return 创建后的规则集合
     */
    List<ChannelAlertRuleResponse> createRules(ChannelAlertRuleBatchSaveRequest request);

    /**
     * 更新渠道预警规则。
     *
     * @param id 规则 ID
     * @param request 保存请求
     * @return 更新后的规则
     */
    ChannelAlertRuleResponse updateRule(Long id, ChannelAlertRuleSaveRequest request);

    /**
     * 查询同一渠道、业务类型和支付方式维度下的规则集合。
     *
     * @param id 任一规则 ID
     * @return 维度详情
     */
    ChannelAlertRuleDimensionResponse getRuleDimension(Long id);

    /**
     * 更新同一渠道、业务类型和支付方式维度下的规则集合。
     *
     * @param id 任一规则 ID
     * @param request 维度保存请求
     * @return 更新后的维度详情
     */
    ChannelAlertRuleDimensionResponse updateRuleDimension(Long id, ChannelAlertRuleDimensionSaveRequest request);

    /**
     * 查询渠道预警规则表单选项。
     *
     * @param channelId 渠道 ID
     * @param businessType 业务类型，可为空
     * @param keyword 用户邮箱或模板搜索关键字，可为空
     * @return 表单选项
     */
    ChannelAlertRuleOptionsResponse ruleOptions(Long channelId, String businessType, String keyword);

    /**
     * 更新渠道预警规则状态。
     *
     * @param id 规则 ID
     * @param status 状态：0停用，1启用
     * @return 更新后的规则
     */
    ChannelAlertRuleResponse updateRuleStatus(Long id, Integer status);

    /**
     * 删除渠道预警规则。
     *
     * @param id 规则 ID
     */
    void deleteRule(Long id);

    /**
     * 分页查询渠道预警事件。
     *
     * @param query 查询条件
     * @return 事件分页结果
     */
    PageResult<ChannelAlertEventResponse> pageEvents(ChannelAlertEventQuery query);

    /**
     * 查询渠道预警事件详情。
     *
     * @param id 事件 ID
     * @return 事件详情
     */
    ChannelAlertEventResponse getEvent(Long id);

    /**
     * 人工确认渠道预警事件。
     *
     * @param id 事件 ID
     * @param request 确认请求
     * @return 确认后的事件
     */
    ChannelAlertEventResponse acknowledgeEvent(Long id, AlertEventAcknowledgeRequest request);

    /**
     * 删除渠道预警事件。
     *
     * @param id 事件 ID
     */
    void deleteEvent(Long id);

    /**
     * 分页查询渠道预警通知日志。
     *
     * @param query 查询条件
     * @return 通知日志分页结果
     */
    PageResult<ChannelAlertNotifyLogResponse> pageNotifyLogs(ChannelAlertNotifyLogQuery query);
}
