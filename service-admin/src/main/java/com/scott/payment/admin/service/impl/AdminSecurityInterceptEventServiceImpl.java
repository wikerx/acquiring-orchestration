package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventMarkRequest;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventResponse;
import com.scott.payment.admin.service.AdminSecurityInterceptEventService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.security.entity.SecurityInterceptEventDO;
import com.scott.payment.component.db.security.mapper.SecurityInterceptEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSecurityInterceptEventServiceImpl
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 安全拦截事件后台服务实现，位于 service-admin 服务实现层，提供脱敏事件检索、详情和人工处理状态维护。
 * @status : create
 */
@Service
public class AdminSecurityInterceptEventServiceImpl implements AdminSecurityInterceptEventService {

    private static final int PROCESS_UNHANDLED = 0;
    private static final int PROCESS_HANDLED = 1;
    private static final int PROCESS_IGNORED = 2;
    private static final int EXPORT_LIMIT = 10000;
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";

    private final SecurityInterceptEventMapper eventMapper;

    /**
     * 创建安全拦截事件服务实现。
     *
     * @param eventMapper 安全事件 Mapper
     */
    public AdminSecurityInterceptEventServiceImpl(SecurityInterceptEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    /**
     * 分页查询安全拦截事件。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<SecurityInterceptEventResponse> pageEvents(SecurityInterceptEventQuery query) {
        SecurityInterceptEventQuery condition = query == null ? new SecurityInterceptEventQuery() : query;
        IPage<SecurityInterceptEventDO> page = eventMapper.selectPage(
                new Page<>(condition.safePageNo(), condition.safePageSize()),
                buildWrapper(condition)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream()
                .map(this::toResponse)
                .toList());
    }

    /**
     * 按查询条件列出安全拦截事件，用于导出。
     *
     * @param query 查询条件
     * @return 事件列表
     */
    @Override
    public List<SecurityInterceptEventResponse> listEvents(SecurityInterceptEventQuery query) {
        SecurityInterceptEventQuery condition = query == null ? new SecurityInterceptEventQuery() : query;
        LambdaQueryWrapper<SecurityInterceptEventDO> wrapper = buildWrapper(condition)
                .last("LIMIT " + EXPORT_LIMIT);
        return eventMapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询安全拦截事件详情。
     *
     * @param id 事件主键
     * @return 事件详情
     */
    @Override
    public SecurityInterceptEventResponse getEvent(Long id) {
        return toResponse(requireEvent(id));
    }

    /**
     * 标记安全拦截事件处理状态。
     *
     * @param id      事件主键
     * @param request 处理请求
     * @return 更新后的事件详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SecurityInterceptEventResponse markEvent(Long id, SecurityInterceptEventMarkRequest request) {
        if (request == null) {
            throw badRequest("处理请求不能为空");
        }
        int processStatus = normalizeProcessStatus(request.getProcessStatus());
        SecurityInterceptEventDO row = requireEvent(id);
        LocalDateTime now = LocalDateTime.now();
        row.setProcessStatus(processStatus);
        row.setProcessRemark(trimToNull(request.getProcessRemark()));
        row.setProcessedBy(currentOperatorName());
        row.setProcessedTime(now);
        row.setGmtModified(now);
        eventMapper.updateById(row);
        return getEvent(id);
    }

    private LambdaQueryWrapper<SecurityInterceptEventDO> buildWrapper(SecurityInterceptEventQuery query) {
        String eventNo = trimToNull(query.getEventNo());
        String sourceLayer = trimToNull(query.getSourceLayer());
        String eventType = trimToNull(query.getEventType());
        String riskLevel = trimToNull(query.getRiskLevel());
        String action = trimToNull(query.getAction());
        String merchantId = trimToNull(query.getMerchantId());
        String clientIp = trimToNull(query.getClientIp());
        String requestPath = trimToNull(query.getRequestPath());
        String traceId = trimToNull(query.getTraceId());
        String requestId = trimToNull(query.getRequestId());
        String hitRuleCode = trimToNull(query.getHitRuleCode());
        QueryTimeRange timeRange = normalizeQueryTimeRange(query.getBeginTime(), query.getEndTime(), query.getQueryTimeZone());
        return Wrappers.<SecurityInterceptEventDO>lambdaQuery()
                .eq(StringUtils.hasText(eventNo), SecurityInterceptEventDO::getEventNo, eventNo)
                .eq(StringUtils.hasText(sourceLayer), SecurityInterceptEventDO::getSourceLayer, sourceLayer)
                .eq(StringUtils.hasText(eventType), SecurityInterceptEventDO::getEventType, eventType)
                .eq(StringUtils.hasText(riskLevel), SecurityInterceptEventDO::getRiskLevel, riskLevel)
                .eq(StringUtils.hasText(action), SecurityInterceptEventDO::getAction, action)
                .eq(StringUtils.hasText(merchantId), SecurityInterceptEventDO::getMerchantId, merchantId)
                .like(StringUtils.hasText(clientIp), SecurityInterceptEventDO::getClientIp, clientIp)
                .like(StringUtils.hasText(requestPath), SecurityInterceptEventDO::getRequestPath, requestPath)
                .eq(StringUtils.hasText(traceId), SecurityInterceptEventDO::getTraceId, traceId)
                .eq(StringUtils.hasText(requestId), SecurityInterceptEventDO::getRequestId, requestId)
                .eq(StringUtils.hasText(hitRuleCode), SecurityInterceptEventDO::getHitRuleCode, hitRuleCode)
                .eq(query.getProcessStatus() != null, SecurityInterceptEventDO::getProcessStatus, normalizeQueryProcessStatus(query.getProcessStatus()))
                .ge(timeRange.beginTime() != null, SecurityInterceptEventDO::getEventTime, timeRange.beginTime())
                .le(timeRange.endTime() != null, SecurityInterceptEventDO::getEventTime, timeRange.endTime())
                .orderByDesc(SecurityInterceptEventDO::getEventTime)
                .orderByDesc(SecurityInterceptEventDO::getId);
    }

    private QueryTimeRange normalizeQueryTimeRange(LocalDateTime beginTime,
                                                   LocalDateTime endTime,
                                                   String queryTimeZone) {
        if (beginTime == null && endTime == null) {
            return new QueryTimeRange(null, null);
        }
        ZoneId queryZone = resolveQueryZone(queryTimeZone);
        if (beginTime != null && endTime != null && beginTime.isAfter(endTime)) {
            throw badRequest("beginTime must not be after endTime");
        }
        ZoneId eventZone = ZoneId.of(DEFAULT_QUERY_TIME_ZONE);
        return new QueryTimeRange(
                convertBetweenZones(beginTime, queryZone, eventZone),
                convertBetweenZones(endTime, queryZone, eventZone)
        );
    }

    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : DEFAULT_QUERY_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "queryTimeZone is invalid", exception);
        }
    }

    private String normalizeZoneId(String zone) {
        if (!StringUtils.hasText(zone)) {
            return DEFAULT_QUERY_TIME_ZONE;
        }
        String normalized = zone.trim();
        String upper = normalized.toUpperCase();
        if ("UTC".equals(upper) || "GMT".equals(upper)) {
            return upper;
        }
        if (upper.startsWith("UTC+") || upper.startsWith("UTC-")
                || upper.startsWith("GMT+") || upper.startsWith("GMT-")) {
            String prefix = upper.substring(0, 3);
            String offset = upper.substring(3);
            if (offset.matches("[+-]\\d{1,2}")) {
                return prefix + String.format("%+03d:00", Integer.parseInt(offset));
            }
            if (offset.matches("[+-]\\d{1,2}:\\d{2}")) {
                String[] parts = offset.substring(1).split(":");
                return prefix + offset.charAt(0) + String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
            }
        }
        return normalized;
    }

    private LocalDateTime convertBetweenZones(LocalDateTime sourceTime, ZoneId sourceZone, ZoneId targetZone) {
        if (sourceTime == null) {
            return null;
        }
        return sourceTime.atZone(sourceZone).withZoneSameInstant(targetZone).toLocalDateTime();
    }

    private SecurityInterceptEventDO requireEvent(Long id) {
        if (id == null) {
            throw badRequest("安全拦截事件不存在");
        }
        SecurityInterceptEventDO row = eventMapper.selectById(id);
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "安全拦截事件不存在");
        }
        return row;
    }

    private SecurityInterceptEventResponse toResponse(SecurityInterceptEventDO row) {
        SecurityInterceptEventResponse response = new SecurityInterceptEventResponse();
        response.setId(row.getId());
        response.setEventNo(row.getEventNo());
        response.setEventTime(row.getEventTime());
        response.setSourceLayer(row.getSourceLayer());
        response.setEventType(row.getEventType());
        response.setRiskLevel(row.getRiskLevel());
        response.setAction(row.getAction());
        response.setMerchantId(row.getMerchantId());
        response.setClientIp(row.getClientIp());
        response.setRequestMethod(row.getRequestMethod());
        response.setRequestPath(row.getRequestPath());
        response.setTraceId(row.getTraceId());
        response.setRequestId(row.getRequestId());
        response.setUserAgent(row.getUserAgent());
        response.setReasonCode(row.getReasonCode());
        response.setReasonMessage(row.getReasonMessage());
        response.setServiceName(row.getServiceName());
        response.setHitRuleCode(row.getHitRuleCode());
        response.setHeaderSummary(row.getHeaderSummary());
        response.setProcessStatus(row.getProcessStatus());
        response.setProcessRemark(row.getProcessRemark());
        response.setProcessedBy(row.getProcessedBy());
        response.setProcessedTime(row.getProcessedTime());
        response.setGmtCreate(row.getGmtCreate());
        response.setGmtModified(row.getGmtModified());
        return response;
    }

    private int normalizeQueryProcessStatus(Integer status) {
        if (status == null) {
            return PROCESS_UNHANDLED;
        }
        if (status == PROCESS_HANDLED || status == PROCESS_IGNORED) {
            return status;
        }
        return PROCESS_UNHANDLED;
    }

    private int normalizeProcessStatus(Integer status) {
        if (status == null || (status != PROCESS_HANDLED && status != PROCESS_IGNORED)) {
            throw badRequest("处理状态只允许已处理或忽略");
        }
        return status;
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return "admin";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private record QueryTimeRange(LocalDateTime beginTime, LocalDateTime endTime) {
    }
}
