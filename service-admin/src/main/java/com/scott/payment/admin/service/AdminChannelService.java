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
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : AdminChannelService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface AdminChannelService {

    /**
     * 执行 page Channels 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery query);

    /**
     * 执行 list Channel Options 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<ChannelOption> listChannelOptions();

    /**
     * 执行 get Channel 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    ChannelInfoResponse getChannel(Long id);

    /**
     * 执行 create Channel 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    ChannelInfoResponse createChannel(ChannelInfoSaveRequest request);

    /**
     * 执行 update Channel 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request);

    /**
     * 执行 update Channel Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    ChannelInfoResponse updateChannelStatus(Long id, Integer status);

    /**
     * 执行 delete Channel 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deleteChannel(Long id);

    /**
     * 执行 page Capabilities 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery query);

    /**
     * 执行 get Capability 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    CapabilityResponse getCapability(Long id);

    /**
     * 执行 create Capability 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    CapabilityResponse createCapability(CapabilitySaveRequest request);

    /**
     * 执行 update Capability 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request);

    /**
     * 执行 update Capability Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    CapabilityResponse updateCapabilityStatus(Long id, Integer status);

    /**
     * 执行 update Capability Support 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param support3ds support3ds 输入值，含义由调用方法名称和所属业务对象限定
     * @param supportIncrementalAuthorization support Incremental Authorization 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization);

    /**
     * 执行 delete Capability 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deleteCapability(Long id);

    /**
     * 执行 page Limits 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<LimitResponse> pageLimits(LimitQuery query);

    /**
     * 执行 get Limit 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    LimitResponse getLimit(Long id);

    /**
     * 执行 create Limit 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    LimitResponse createLimit(LimitSaveRequest request);

    /**
     * 执行 create Limits 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<LimitResponse> createLimits(LimitBatchSaveRequest request);

    /**
     * 执行 save Limit Dimension 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request);

    /**
     * 执行 update Limit 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    LimitResponse updateLimit(Long id, LimitSaveRequest request);

    /**
     * 执行 update Limit Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    LimitResponse updateLimitStatus(Long id, Integer status);

    /**
     * 执行 delete Limit 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deleteLimit(Long id);

    /**
     * 执行 page Mids 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<ChannelMidConfigResponse> pageMids(ChannelMidConfigQuery query);

    /**
     * 执行 get Mid 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    ChannelMidConfigResponse getMid(Long id);

    /**
     * 执行 create Mid 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    ChannelMidConfigResponse createMid(ChannelMidConfigSaveRequest request);

    /**
     * 执行 update Mid 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    ChannelMidConfigResponse updateMid(Long id, ChannelMidConfigSaveRequest request);

    /**
     * 执行 update Mid Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    ChannelMidConfigResponse updateMidStatus(Long id, Integer status);

    /**
     * 执行 delete Mid 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deleteMid(Long id);

    /**
     * 执行 page Mid Bindings 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<MerchantChannelMidBindingResponse> pageMidBindings(MerchantChannelMidBindingQuery query);

    /**
     * 执行 get Mid Binding 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    MerchantChannelMidBindingResponse getMidBinding(Long id);

    /**
     * 执行 create Mid Binding 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    MerchantChannelMidBindingResponse createMidBinding(MerchantChannelMidBindingSaveRequest request);

    /**
     * 执行 update Mid Binding 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    MerchantChannelMidBindingResponse updateMidBinding(Long id, MerchantChannelMidBindingSaveRequest request);

    /**
     * 执行 update Mid Binding Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    MerchantChannelMidBindingResponse updateMidBindingStatus(Long id, Integer status);

    /**
     * 执行 delete Mid Binding 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deleteMidBinding(Long id);

}
