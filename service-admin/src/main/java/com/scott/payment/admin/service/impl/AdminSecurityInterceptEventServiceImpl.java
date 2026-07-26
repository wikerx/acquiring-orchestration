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

    /**
     * PROCESS UNHANDLED，用于保存 Admin Security Intercept Event Service Impl 中与 processunhandled 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int PROCESS_UNHANDLED = 0;
    /**
     * PROCESS HANDLED，用于保存 Admin Security Intercept Event Service Impl 中与 processhandled 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int PROCESS_HANDLED = 1;
    /**
     * PROCESS IGNORED，用于保存 Admin Security Intercept Event Service Impl 中与 processignored 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int PROCESS_IGNORED = 2;
    /**
     * EXPORT LIMIT，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；不允许为空；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int EXPORT_LIMIT = 10000;
    /**
     * DEFAULT QUERY TIME ZONE，用于保存 Admin Security Intercept Event Service Impl 中与 defaultquerytimezone 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_QUERY_TIME_ZONE = "Asia/Shanghai";

    /**
     * event Mapper 依赖，用于 Admin Security Intercept Event Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
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

    /**
     * 解析resolve查询zone，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param queryTimeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 构造、转换或解析后的业务值
     */
    private ZoneId resolveQueryZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : DEFAULT_QUERY_TIME_ZONE;
        try {
            return ZoneId.of(normalizeZoneId(zone));
        } catch (DateTimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "queryTimeZone is invalid", exception);
        }
    }

    /**
     * 解析normalizezoneID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param zone zone 输入值，参与 zone 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造betweenzones对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param sourceTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param sourceZone source Zone 输入值，参与 来源zone 的查询、校验、转换、写入或日志摘要
     * @param targetZone target Zone 输入值，参与 targetzone 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime convertBetweenZones(LocalDateTime sourceTime, ZoneId sourceZone, ZoneId targetZone) {
        if (sourceTime == null) {
            return null;
        }
        return sourceTime.atZone(sourceZone).withZoneSameInstant(targetZone).toLocalDateTime();
    }

    /**
     * 校验事件输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 构造响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 解析normalize查询process状态，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private int normalizeQueryProcessStatus(Integer status) {
        if (status == null) {
            return PROCESS_UNHANDLED;
        }
        if (status == PROCESS_HANDLED || status == PROCESS_IGNORED) {
            return status;
        }
        return PROCESS_UNHANDLED;
    }

    /**
     * 解析normalizeprocess状态，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private int normalizeProcessStatus(Integer status) {
        if (status == null || (status != PROCESS_HANDLED && status != PROCESS_IGNORED)) {
            throw badRequest("处理状态只允许已处理或忽略");
        }
        return status;
    }

    /**
     * 整理当前操作人名称，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 规范化trimtonull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 整理bad请求，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private record QueryTimeRange(LocalDateTime beginTime, LocalDateTime endTime) {
    }
}
