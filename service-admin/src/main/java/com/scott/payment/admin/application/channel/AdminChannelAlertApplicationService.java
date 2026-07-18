package com.scott.payment.admin.application.channel;

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
import com.scott.payment.admin.service.AdminChannelAlertService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelAlertApplicationService
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警管理应用服务，位于 service-admin 应用层，编排后台入口与预警规则服务能力。
 * @status : create
 */
@Service
public class AdminChannelAlertApplicationService {

    private final AdminChannelAlertService channelAlertService;

    /**
     * 创建渠道预警管理应用服务。
     *
     * @param channelAlertService 渠道预警管理服务
     */
    public AdminChannelAlertApplicationService(AdminChannelAlertService channelAlertService) {
        this.channelAlertService = channelAlertService;
    }

    public PageResult<ChannelAlertRuleResponse> pageRules(ChannelAlertRuleQuery query) {
        return channelAlertService.pageRules(query);
    }

    public ChannelAlertRuleResponse getRule(Long id) {
        return channelAlertService.getRule(id);
    }

    public ChannelAlertRuleResponse createRule(ChannelAlertRuleSaveRequest request) {
        return channelAlertService.createRule(request);
    }

    public List<ChannelAlertRuleResponse> createRules(ChannelAlertRuleBatchSaveRequest request) {
        return channelAlertService.createRules(request);
    }

    public ChannelAlertRuleResponse updateRule(Long id, ChannelAlertRuleSaveRequest request) {
        return channelAlertService.updateRule(id, request);
    }

    public ChannelAlertRuleDimensionResponse getRuleDimension(Long id) {
        return channelAlertService.getRuleDimension(id);
    }

    public ChannelAlertRuleDimensionResponse updateRuleDimension(Long id, ChannelAlertRuleDimensionSaveRequest request) {
        return channelAlertService.updateRuleDimension(id, request);
    }

    public ChannelAlertRuleOptionsResponse ruleOptions(Long channelId, String businessType, String keyword) {
        return channelAlertService.ruleOptions(channelId, businessType, keyword);
    }

    public ChannelAlertRuleResponse updateRuleStatus(Long id, Integer status) {
        return channelAlertService.updateRuleStatus(id, status);
    }

    public void deleteRule(Long id) {
        channelAlertService.deleteRule(id);
    }

    public PageResult<ChannelAlertEventResponse> pageEvents(ChannelAlertEventQuery query) {
        return channelAlertService.pageEvents(query);
    }

    public ChannelAlertEventResponse getEvent(Long id) {
        return channelAlertService.getEvent(id);
    }

    public ChannelAlertEventResponse acknowledgeEvent(Long id, AlertEventAcknowledgeRequest request) {
        return channelAlertService.acknowledgeEvent(id, request);
    }

    public void deleteEvent(Long id) {
        channelAlertService.deleteEvent(id);
    }

    public PageResult<ChannelAlertNotifyLogResponse> pageNotifyLogs(ChannelAlertNotifyLogQuery query) {
        return channelAlertService.pageNotifyLogs(query);
    }
}
