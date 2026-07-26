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
 * @description : Admin Channel Service 服务契约，位于 运营后台服务，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
public interface AdminChannelService {

    /**
     * 查询渠道，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery query);

    /**
     * 查询渠道选项，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    List<ChannelOption> listChannelOptions();

    /**
     * 查询渠道，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    ChannelInfoResponse getChannel(Long id);

    /**
     * 创建渠道，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    ChannelInfoResponse createChannel(ChannelInfoSaveRequest request);

    /**
     * 更新渠道，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request);

    /**
     * 更新渠道状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    ChannelInfoResponse updateChannelStatus(Long id, Integer status);

    /**
     * 删除或停用渠道，调用方需保证权限和状态允许该操作。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在、权限满足且状态允许删除或停用。
     * 该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。
     * 异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteChannel(Long id);

    /**
     * 查询渠道能力，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery query);

    /**
     * 查询渠道能力，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    CapabilityResponse getCapability(Long id);

    /**
     * 创建渠道能力，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    CapabilityResponse createCapability(CapabilitySaveRequest request);

    /**
     * 更新渠道能力，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request);

    /**
     * 更新渠道能力状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    CapabilityResponse updateCapabilityStatus(Long id, Integer status);

    /**
     * 更新渠道能力支持标识，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param support3ds support 3DS 输入值，参与 support3ds 的查询、校验、转换、写入或日志摘要
     * @param supportIncrementalAuthorization support Incremental Authorization 输入值，参与 supportincrementalauthorization 的查询、校验、转换、写入或日志摘要
     * @return 写入、更新或删除后的处理结果
     */
    CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization);

    /**
     * 删除或停用渠道能力，调用方需保证权限和状态允许该操作。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在、权限满足且状态允许删除或停用。
     * 该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。
     * 异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteCapability(Long id);

    /**
     * 查询限额，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<LimitResponse> pageLimits(LimitQuery query);

    /**
     * 查询限额，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    LimitResponse getLimit(Long id);

    /**
     * 创建限额，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    LimitResponse createLimit(LimitSaveRequest request);

    /**
     * 创建限额，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    List<LimitResponse> createLimits(LimitBatchSaveRequest request);

    /**
     * 创建限额维度，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request);

    /**
     * 更新限额，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    LimitResponse updateLimit(Long id, LimitSaveRequest request);

    /**
     * 更新limit状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    LimitResponse updateLimitStatus(Long id, Integer status);

    /**
     * 删除或停用限额，调用方需保证权限和状态允许该操作。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在、权限满足且状态允许删除或停用。
     * 该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。
     * 异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteLimit(Long id);

    /**
     * 查询渠道 MID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<ChannelMidConfigResponse> pageMids(ChannelMidConfigQuery query);

    /**
     * 查询渠道 MID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    ChannelMidConfigResponse getMid(Long id);

    /**
     * 创建渠道 MID，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    ChannelMidConfigResponse createMid(ChannelMidConfigSaveRequest request);

    /**
     * 更新渠道 MID，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    ChannelMidConfigResponse updateMid(Long id, ChannelMidConfigSaveRequest request);

    /**
     * 更新mid状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    ChannelMidConfigResponse updateMidStatus(Long id, Integer status);

    /**
     * 删除或停用渠道 MID，调用方需保证权限和状态允许该操作。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在、权限满足且状态允许删除或停用。
     * 该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。
     * 异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteMid(Long id);

    /**
     * 查询渠道 MID 绑定，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<MerchantChannelMidBindingResponse> pageMidBindings(MerchantChannelMidBindingQuery query);

    /**
     * 查询渠道 MID 绑定，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    MerchantChannelMidBindingResponse getMidBinding(Long id);

    /**
     * 创建渠道 MID 绑定，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    MerchantChannelMidBindingResponse createMidBinding(MerchantChannelMidBindingSaveRequest request);

    /**
     * 更新渠道 MID 绑定，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    MerchantChannelMidBindingResponse updateMidBinding(Long id, MerchantChannelMidBindingSaveRequest request);

    /**
     * 更新midbinding状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    MerchantChannelMidBindingResponse updateMidBindingStatus(Long id, Integer status);

    /**
     * 删除或停用渠道 MID 绑定，调用方需保证权限和状态允许该操作。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在、权限满足且状态允许删除或停用。
     * 该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。
     * 异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteMidBinding(Long id);

}
