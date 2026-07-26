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

    /**
     * channel Alert Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
     * 编排 page Rules 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<ChannelAlertRuleResponse> pageRules(ChannelAlertRuleQuery query) {
        return channelAlertService.pageRules(query);
    }

    /**
     * 编排 get Rule 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertRuleResponse getRule(Long id) {
        return channelAlertService.getRule(id);
    }

    /**
     * 编排 create Rule 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertRuleResponse createRule(ChannelAlertRuleSaveRequest request) {
        return channelAlertService.createRule(request);
    }

    /**
     * 编排 create Rules 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public List<ChannelAlertRuleResponse> createRules(ChannelAlertRuleBatchSaveRequest request) {
        return channelAlertService.createRules(request);
    }

    /**
     * 编排 update Rule 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertRuleResponse updateRule(Long id, ChannelAlertRuleSaveRequest request) {
        return channelAlertService.updateRule(id, request);
    }

    /**
     * 编排 get Rule Dimension 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertRuleDimensionResponse getRuleDimension(Long id) {
        return channelAlertService.getRuleDimension(id);
    }

    /**
     * 编排 update Rule Dimension 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertRuleDimensionResponse updateRuleDimension(Long id, ChannelAlertRuleDimensionSaveRequest request) {
        return channelAlertService.updateRuleDimension(id, request);
    }

    /**
     * 编排 rule Options 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param channelId channel Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param businessType business Type 输入值，含义由调用方法名称和所属业务对象限定
     * @param keyword keyword 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertRuleOptionsResponse ruleOptions(Long channelId, String businessType, String keyword) {
        return channelAlertService.ruleOptions(channelId, businessType, keyword);
    }

    /**
     * 编排 update Rule Status 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertRuleResponse updateRuleStatus(Long id, Integer status) {
        return channelAlertService.updateRuleStatus(id, status);
    }

    /**
     * 编排 delete Rule 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void deleteRule(Long id) {
        channelAlertService.deleteRule(id);
    }

    /**
     * 编排 page Events 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<ChannelAlertEventResponse> pageEvents(ChannelAlertEventQuery query) {
        return channelAlertService.pageEvents(query);
    }

    /**
     * 编排 get Event 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertEventResponse getEvent(Long id) {
        return channelAlertService.getEvent(id);
    }

    /**
     * 编排 acknowledge Event 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelAlertEventResponse acknowledgeEvent(Long id, AlertEventAcknowledgeRequest request) {
        return channelAlertService.acknowledgeEvent(id, request);
    }

    /**
     * 编排 delete Event 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void deleteEvent(Long id) {
        channelAlertService.deleteEvent(id);
    }

    /**
     * 编排 page Notify Logs 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelAlertApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<ChannelAlertNotifyLogResponse> pageNotifyLogs(ChannelAlertNotifyLogQuery query) {
        return channelAlertService.pageNotifyLogs(query);
    }
}
