package com.scott.payment.admin.service;

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
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Channel 服务契约，位于 service-admin 的服务契约层，用于定义调用契约和职责边界。
 * @status : create
 */
public interface AdminChannelService {

    PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery query);

    List<ChannelOption> listChannelOptions();

    ChannelInfoResponse getChannel(Long id);

    ChannelInfoResponse createChannel(ChannelInfoSaveRequest request);

    ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request);

    ChannelInfoResponse updateChannelStatus(Long id, Integer status);

    void deleteChannel(Long id);

    PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery query);

    CapabilityResponse getCapability(Long id);

    CapabilityResponse createCapability(CapabilitySaveRequest request);

    CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request);

    CapabilityResponse updateCapabilityStatus(Long id, Integer status);

    CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization);

    void deleteCapability(Long id);

    PageResult<LimitResponse> pageLimits(LimitQuery query);

    LimitResponse getLimit(Long id);

    LimitResponse createLimit(LimitSaveRequest request);

    List<LimitResponse> createLimits(LimitBatchSaveRequest request);

    List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request);

    LimitResponse updateLimit(Long id, LimitSaveRequest request);

    LimitResponse updateLimitStatus(Long id, Integer status);

    void deleteLimit(Long id);

    PageResult<ChannelMidConfigResponse> pageMids(ChannelMidConfigQuery query);

    ChannelMidConfigResponse getMid(Long id);

    ChannelMidConfigResponse createMid(ChannelMidConfigSaveRequest request);

    ChannelMidConfigResponse updateMid(Long id, ChannelMidConfigSaveRequest request);

    ChannelMidConfigResponse updateMidStatus(Long id, Integer status);

    void deleteMid(Long id);

    PageResult<MerchantChannelMidBindingResponse> pageMidBindings(MerchantChannelMidBindingQuery query);

    MerchantChannelMidBindingResponse getMidBinding(Long id);

    MerchantChannelMidBindingResponse createMidBinding(MerchantChannelMidBindingSaveRequest request);

    MerchantChannelMidBindingResponse updateMidBinding(Long id, MerchantChannelMidBindingSaveRequest request);

    MerchantChannelMidBindingResponse updateMidBindingStatus(Long id, Integer status);

    void deleteMidBinding(Long id);

}
