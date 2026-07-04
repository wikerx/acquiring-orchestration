package com.scott.payment.admin.service;

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
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * 管理后台渠道管理服务。
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

}
