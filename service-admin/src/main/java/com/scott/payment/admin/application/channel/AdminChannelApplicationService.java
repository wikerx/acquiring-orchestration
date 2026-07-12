package com.scott.payment.admin.application.channel;

import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilitySaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelOption;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingSaveRequest;
import com.scott.payment.admin.service.AdminChannelService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 渠道管理Admin Channel Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminChannelApplicationService {

    /**
     * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminChannelService adminChannelService;

    public AdminChannelApplicationService(AdminChannelService adminChannelService) {
        this.adminChannelService = adminChannelService;
    }

    /**
     * 查询渠道管理列表或分页数据，供页面筛选和展示使用。
     * @param query 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery query) {
        return adminChannelService.pageChannels(query);
    }

    /**
     * 查询渠道管理列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */

    public List<ChannelOption> listChannelOptions() {
        return adminChannelService.listChannelOptions();
    }

    /**
     * 获取渠道管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public ChannelInfoResponse getChannel(Long id) {
        return adminChannelService.getChannel(id);
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public ChannelInfoResponse createChannel(ChannelInfoSaveRequest request) {
        return adminChannelService.createChannel(request);
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request) {
        return adminChannelService.updateChannel(id, request);
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public ChannelInfoResponse updateChannelStatus(Long id, Integer status) {
        return adminChannelService.updateChannelStatus(id, status);
    }

    /**
     * 删除渠道管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void deleteChannel(Long id) {
        adminChannelService.deleteChannel(id);
    }

    /**
     * 查询渠道管理列表或分页数据，供页面筛选和展示使用。
     * @param query 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery query) {
        return adminChannelService.pageCapabilities(query);
    }

    /**
     * 获取渠道管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public CapabilityResponse getCapability(Long id) {
        return adminChannelService.getCapability(id);
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public CapabilityResponse createCapability(CapabilitySaveRequest request) {
        return adminChannelService.createCapability(request);
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request) {
        return adminChannelService.updateCapability(id, request);
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public CapabilityResponse updateCapabilityStatus(Long id, Integer status) {
        return adminChannelService.updateCapabilityStatus(id, status);
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param support3ds 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param supportIncrementalAuthorization 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization) {
        return adminChannelService.updateCapabilitySupport(id, support3ds, supportIncrementalAuthorization);
    }

    /**
     * 删除渠道管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void deleteCapability(Long id) {
        adminChannelService.deleteCapability(id);
    }

    /**
     * 查询渠道管理列表或分页数据，供页面筛选和展示使用。
     * @param query 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public PageResult<LimitResponse> pageLimits(LimitQuery query) {
        return adminChannelService.pageLimits(query);
    }

    /**
     * 获取渠道管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public LimitResponse getLimit(Long id) {
        return adminChannelService.getLimit(id);
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public LimitResponse createLimit(LimitSaveRequest request) {
        return adminChannelService.createLimit(request);
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public List<LimitResponse> createLimits(LimitBatchSaveRequest request) {
        return adminChannelService.createLimits(request);
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request) {
        return adminChannelService.saveLimitDimension(request);
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public LimitResponse updateLimit(Long id, LimitSaveRequest request) {
        return adminChannelService.updateLimit(id, request);
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public LimitResponse updateLimitStatus(Long id, Integer status) {
        return adminChannelService.updateLimitStatus(id, status);
    }

    /**
     * 删除渠道管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */

    public void deleteLimit(Long id) {
        adminChannelService.deleteLimit(id);
    }

    public PageResult<ChannelMidConfigResponse> pageMids(ChannelMidConfigQuery query) {
        return adminChannelService.pageMids(query);
    }

    public ChannelMidConfigResponse getMid(Long id) {
        return adminChannelService.getMid(id);
    }

    public ChannelMidConfigResponse createMid(ChannelMidConfigSaveRequest request) {
        return adminChannelService.createMid(request);
    }

    public ChannelMidConfigResponse updateMid(Long id, ChannelMidConfigSaveRequest request) {
        return adminChannelService.updateMid(id, request);
    }

    public ChannelMidConfigResponse updateMidStatus(Long id, Integer status) {
        return adminChannelService.updateMidStatus(id, status);
    }

    public void deleteMid(Long id) {
        adminChannelService.deleteMid(id);
    }

    public PageResult<MerchantChannelMidBindingResponse> pageMidBindings(MerchantChannelMidBindingQuery query) {
        return adminChannelService.pageMidBindings(query);
    }

    public MerchantChannelMidBindingResponse getMidBinding(Long id) {
        return adminChannelService.getMidBinding(id);
    }

    public MerchantChannelMidBindingResponse createMidBinding(MerchantChannelMidBindingSaveRequest request) {
        return adminChannelService.createMidBinding(request);
    }

    public MerchantChannelMidBindingResponse updateMidBinding(Long id, MerchantChannelMidBindingSaveRequest request) {
        return adminChannelService.updateMidBinding(id, request);
    }

    public MerchantChannelMidBindingResponse updateMidBindingStatus(Long id, Integer status) {
        return adminChannelService.updateMidBindingStatus(id, status);
    }

    public void deleteMidBinding(Long id) {
        adminChannelService.deleteMidBinding(id);
    }

}
