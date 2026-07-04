package com.scott.payment.admin.application.channel;

import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilitySaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelOption;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitSaveRequest;
import com.scott.payment.admin.service.AdminChannelService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理后台渠道管理应用服务。
 *
 * <p>负责渠道管理用例编排，Controller 只处理 HTTP 映射，核心校验和持久化规则由领域服务处理。</p>
 */
@Service
public class AdminChannelApplicationService {

    private final AdminChannelService adminChannelService;

    public AdminChannelApplicationService(AdminChannelService adminChannelService) {
        this.adminChannelService = adminChannelService;
    }

    public PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery query) {
        return adminChannelService.pageChannels(query);
    }

    public List<ChannelOption> listChannelOptions() {
        return adminChannelService.listChannelOptions();
    }

    public ChannelInfoResponse getChannel(Long id) {
        return adminChannelService.getChannel(id);
    }

    public ChannelInfoResponse createChannel(ChannelInfoSaveRequest request) {
        return adminChannelService.createChannel(request);
    }

    public ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request) {
        return adminChannelService.updateChannel(id, request);
    }

    public ChannelInfoResponse updateChannelStatus(Long id, Integer status) {
        return adminChannelService.updateChannelStatus(id, status);
    }

    public void deleteChannel(Long id) {
        adminChannelService.deleteChannel(id);
    }

    public PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery query) {
        return adminChannelService.pageCapabilities(query);
    }

    public CapabilityResponse getCapability(Long id) {
        return adminChannelService.getCapability(id);
    }

    public CapabilityResponse createCapability(CapabilitySaveRequest request) {
        return adminChannelService.createCapability(request);
    }

    public CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request) {
        return adminChannelService.updateCapability(id, request);
    }

    public CapabilityResponse updateCapabilityStatus(Long id, Integer status) {
        return adminChannelService.updateCapabilityStatus(id, status);
    }

    public CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization) {
        return adminChannelService.updateCapabilitySupport(id, support3ds, supportIncrementalAuthorization);
    }

    public void deleteCapability(Long id) {
        adminChannelService.deleteCapability(id);
    }

    public PageResult<LimitResponse> pageLimits(LimitQuery query) {
        return adminChannelService.pageLimits(query);
    }

    public LimitResponse getLimit(Long id) {
        return adminChannelService.getLimit(id);
    }

    public LimitResponse createLimit(LimitSaveRequest request) {
        return adminChannelService.createLimit(request);
    }

    public List<LimitResponse> createLimits(LimitBatchSaveRequest request) {
        return adminChannelService.createLimits(request);
    }

    public List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request) {
        return adminChannelService.saveLimitDimension(request);
    }

    public LimitResponse updateLimit(Long id, LimitSaveRequest request) {
        return adminChannelService.updateLimit(id, request);
    }

    public LimitResponse updateLimitStatus(Long id, Integer status) {
        return adminChannelService.updateLimitStatus(id, status);
    }

    public void deleteLimit(Long id) {
        adminChannelService.deleteLimit(id);
    }

}
