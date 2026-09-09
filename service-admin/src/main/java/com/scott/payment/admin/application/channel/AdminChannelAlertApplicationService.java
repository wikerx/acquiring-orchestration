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
 * @description : admin渠道告警应用服务，位于 运营后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
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

    /**
     * 查询规则；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<ChannelAlertRuleResponse> pageRules(ChannelAlertRuleQuery query) {
        return channelAlertService.pageRules(query);
    }

    /**
     * 查询规则；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public ChannelAlertRuleResponse getRule(Long id) {
        return channelAlertService.getRule(id);
    }

    /**
     * 创建规则，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelAlertRuleResponse createRule(ChannelAlertRuleSaveRequest request) {
        return channelAlertService.createRule(request);
    }

    /**
     * 创建规则，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public List<ChannelAlertRuleResponse> createRules(ChannelAlertRuleBatchSaveRequest request) {
        return channelAlertService.createRules(request);
    }

    /**
     * 更新规则，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelAlertRuleResponse updateRule(Long id, ChannelAlertRuleSaveRequest request) {
        return channelAlertService.updateRule(id, request);
    }

    /**
     * 查询规则维度；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public ChannelAlertRuleDimensionResponse getRuleDimension(Long id) {
        return channelAlertService.getRuleDimension(id);
    }

    /**
     * 更新规则维度，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelAlertRuleDimensionResponse updateRuleDimension(Long id, ChannelAlertRuleDimensionSaveRequest request) {
        return channelAlertService.updateRuleDimension(id, request);
    }

    /**
     * 查询当前管理页面可选择的渠道告警规则选项。
     * @param channelId 可选渠道主键，用于限定告警规则选项
     * @param businessType 可选业务类型编码
     * @param keyword 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 符合条件的渠道、业务类型和规则选项
     */
    public ChannelAlertRuleOptionsResponse ruleOptions(Long channelId, String businessType, String keyword) {
        return channelAlertService.ruleOptions(channelId, businessType, keyword);
    }

    /**
     * 更新规则状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelAlertRuleResponse updateRuleStatus(Long id, Integer status) {
        return channelAlertService.updateRuleStatus(id, status);
    }

    /**
     * 删除或停用规则，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteRule(Long id) {
        channelAlertService.deleteRule(id);
    }

    /**
     * 查询事件；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<ChannelAlertEventResponse> pageEvents(ChannelAlertEventQuery query) {
        return channelAlertService.pageEvents(query);
    }

    /**
     * 查询事件；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public ChannelAlertEventResponse getEvent(Long id) {
        return channelAlertService.getEvent(id);
    }

    /**
     * 确认指定渠道告警事件并记录可信操作人审计信息。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 已写入可信操作人和确认时间的告警事件
     */
    public ChannelAlertEventResponse acknowledgeEvent(Long id, AlertEventAcknowledgeRequest request) {
        return channelAlertService.acknowledgeEvent(id, request);
    }

    /**
     * 删除或停用事件，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteEvent(Long id) {
        channelAlertService.deleteEvent(id);
    }

    /**
     * 查询通知日志；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<ChannelAlertNotifyLogResponse> pageNotifyLogs(ChannelAlertNotifyLogQuery query) {
        return channelAlertService.pageNotifyLogs(query);
    }
}
