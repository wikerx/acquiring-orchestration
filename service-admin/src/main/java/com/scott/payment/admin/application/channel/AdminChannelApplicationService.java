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

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelApplicationService
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : AdminChannelApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminChannelApplicationService {

    /**
     * admin Channel Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminChannelService adminChannelService;

    /**
     * 创建 AdminChannelApplicationService 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param adminChannelService admin Channel Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminChannelApplicationService(AdminChannelService adminChannelService) {
        this.adminChannelService = adminChannelService;
    }

    /**
     * 编排 page Channels 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery query) {
        return adminChannelService.pageChannels(query);
    }

    /**
     * 编排 list Channel Options 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public List<ChannelOption> listChannelOptions() {
        return adminChannelService.listChannelOptions();
    }

    /**
     * 编排 get Channel 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelInfoResponse getChannel(Long id) {
        return adminChannelService.getChannel(id);
    }

    /**
     * 编排 create Channel 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelInfoResponse createChannel(ChannelInfoSaveRequest request) {
        return adminChannelService.createChannel(request);
    }

    /**
     * 编排 update Channel 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request) {
        return adminChannelService.updateChannel(id, request);
    }

    /**
     * 编排 update Channel Status 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelInfoResponse updateChannelStatus(Long id, Integer status) {
        return adminChannelService.updateChannelStatus(id, status);
    }

    /**
     * 编排 delete Channel 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void deleteChannel(Long id) {
        adminChannelService.deleteChannel(id);
    }

    /**
     * 编排 page Capabilities 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery query) {
        return adminChannelService.pageCapabilities(query);
    }

    /**
     * 编排 get Capability 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public CapabilityResponse getCapability(Long id) {
        return adminChannelService.getCapability(id);
    }

    /**
     * 编排 create Capability 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public CapabilityResponse createCapability(CapabilitySaveRequest request) {
        return adminChannelService.createCapability(request);
    }

    /**
     * 编排 update Capability 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request) {
        return adminChannelService.updateCapability(id, request);
    }

    /**
     * 编排 update Capability Status 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public CapabilityResponse updateCapabilityStatus(Long id, Integer status) {
        return adminChannelService.updateCapabilityStatus(id, status);
    }

    /**
     * 编排 update Capability Support 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param support3ds support3ds 输入值，含义由调用方法名称和所属业务对象限定
     * @param supportIncrementalAuthorization support Incremental Authorization 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization) {
        return adminChannelService.updateCapabilitySupport(id, support3ds, supportIncrementalAuthorization);
    }

    /**
     * 编排 delete Capability 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void deleteCapability(Long id) {
        adminChannelService.deleteCapability(id);
    }

    /**
     * 编排 page Limits 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<LimitResponse> pageLimits(LimitQuery query) {
        return adminChannelService.pageLimits(query);
    }

    /**
     * 编排 get Limit 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public LimitResponse getLimit(Long id) {
        return adminChannelService.getLimit(id);
    }

    /**
     * 编排 create Limit 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public LimitResponse createLimit(LimitSaveRequest request) {
        return adminChannelService.createLimit(request);
    }

    /**
     * 编排 create Limits 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public List<LimitResponse> createLimits(LimitBatchSaveRequest request) {
        return adminChannelService.createLimits(request);
    }

    /**
     * 编排 save Limit Dimension 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request) {
        return adminChannelService.saveLimitDimension(request);
    }

    /**
     * 编排 update Limit 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public LimitResponse updateLimit(Long id, LimitSaveRequest request) {
        return adminChannelService.updateLimit(id, request);
    }

    /**
     * 编排 update Limit Status 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public LimitResponse updateLimitStatus(Long id, Integer status) {
        return adminChannelService.updateLimitStatus(id, status);
    }

    /**
     * 编排 delete Limit 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void deleteLimit(Long id) {
        adminChannelService.deleteLimit(id);
    }

    /**
     * 编排 page Mids 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<ChannelMidConfigResponse> pageMids(ChannelMidConfigQuery query) {
        return adminChannelService.pageMids(query);
    }

    /**
     * 编排 get Mid 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelMidConfigResponse getMid(Long id) {
        return adminChannelService.getMid(id);
    }

    /**
     * 编排 create Mid 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelMidConfigResponse createMid(ChannelMidConfigSaveRequest request) {
        return adminChannelService.createMid(request);
    }

    /**
     * 编排 update Mid 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelMidConfigResponse updateMid(Long id, ChannelMidConfigSaveRequest request) {
        return adminChannelService.updateMid(id, request);
    }

    /**
     * 编排 update Mid Status 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public ChannelMidConfigResponse updateMidStatus(Long id, Integer status) {
        return adminChannelService.updateMidStatus(id, status);
    }

    /**
     * 编排 delete Mid 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void deleteMid(Long id) {
        adminChannelService.deleteMid(id);
    }

    /**
     * 编排 page Mid Bindings 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<MerchantChannelMidBindingResponse> pageMidBindings(MerchantChannelMidBindingQuery query) {
        return adminChannelService.pageMidBindings(query);
    }

    /**
     * 编排 get Mid Binding 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public MerchantChannelMidBindingResponse getMidBinding(Long id) {
        return adminChannelService.getMidBinding(id);
    }

    /**
     * 编排 create Mid Binding 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public MerchantChannelMidBindingResponse createMidBinding(MerchantChannelMidBindingSaveRequest request) {
        return adminChannelService.createMidBinding(request);
    }

    /**
     * 编排 update Mid Binding 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public MerchantChannelMidBindingResponse updateMidBinding(Long id, MerchantChannelMidBindingSaveRequest request) {
        return adminChannelService.updateMidBinding(id, request);
    }

    /**
     * 编排 update Mid Binding Status 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public MerchantChannelMidBindingResponse updateMidBindingStatus(Long id, Integer status) {
        return adminChannelService.updateMidBindingStatus(id, status);
    }

    /**
     * 编排 delete Mid Binding 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void deleteMidBinding(Long id) {
        adminChannelService.deleteMidBinding(id);
    }

}
