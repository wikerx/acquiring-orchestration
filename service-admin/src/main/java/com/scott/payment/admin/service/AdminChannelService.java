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
 * @description : admin渠道服务契约，位于 运营后台服务，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
public interface AdminChannelService {

    /**
     * 查询渠道；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery query);

    /**
     * 查询渠道选项；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    List<ChannelOption> listChannelOptions();

    /**
     * 查询渠道；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    ChannelInfoResponse getChannel(Long id);

    /**
     * 创建渠道，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    ChannelInfoResponse createChannel(ChannelInfoSaveRequest request);

    /**
     * 更新渠道，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request);

    /**
     * 更新渠道状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    ChannelInfoResponse updateChannelStatus(Long id, Integer status);

    /**
     * 删除或停用渠道，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteChannel(Long id);

    /**
     * 查询渠道能力；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery query);

    /**
     * 查询渠道能力；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    CapabilityResponse getCapability(Long id);

    /**
     * 创建渠道能力，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    CapabilityResponse createCapability(CapabilitySaveRequest request);

    /**
     * 更新渠道能力，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request);

    /**
     * 更新渠道能力状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    CapabilityResponse updateCapabilityStatus(Long id, Integer status);

    /**
     * 更新渠道能力支持标识，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param support3ds 是否支持 3DS，取值为 0 或 1
     * @param supportIncrementalAuthorization 是否支持增量授权，取值为 0 或 1
     * @return 写入、更新或删除后的处理结果
     */
    CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization);

    /**
     * 删除或停用渠道能力，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteCapability(Long id);

    /**
     * 查询限额；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<LimitResponse> pageLimits(LimitQuery query);

    /**
     * 查询限额；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    LimitResponse getLimit(Long id);

    /**
     * 创建限额，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    LimitResponse createLimit(LimitSaveRequest request);

    /**
     * 创建限额，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    List<LimitResponse> createLimits(LimitBatchSaveRequest request);

    /**
     * 创建限额维度，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request);

    /**
     * 更新限额，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    LimitResponse updateLimit(Long id, LimitSaveRequest request);

    /**
     * 更新渠道限额规则状态。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    LimitResponse updateLimitStatus(Long id, Integer status);

    /**
     * 删除或停用限额，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteLimit(Long id);

    /**
     * 查询{@code pageMids}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<ChannelMidConfigResponse> pageMids(ChannelMidConfigQuery query);

    /**
     * 查询{@code getMid}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    ChannelMidConfigResponse getMid(Long id);

    /**
     * 创建{@code createMid}，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    ChannelMidConfigResponse createMid(ChannelMidConfigSaveRequest request);

    /**
     * 更新渠道 MID，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    ChannelMidConfigResponse updateMid(Long id, ChannelMidConfigSaveRequest request);

    /**
     * 更新渠道 MID 配置状态。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    ChannelMidConfigResponse updateMidStatus(Long id, Integer status);

    /**
     * 删除或停用渠道 MID，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteMid(Long id);

    /**
     * 查询{@code pageMidBindings}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<MerchantChannelMidBindingResponse> pageMidBindings(MerchantChannelMidBindingQuery query);

    /**
     * 查询{@code getMidBinding}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    MerchantChannelMidBindingResponse getMidBinding(Long id);

    /**
     * 创建{@code createMidBinding}，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    MerchantChannelMidBindingResponse createMidBinding(MerchantChannelMidBindingSaveRequest request);

    /**
     * 更新渠道 MID 绑定，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    MerchantChannelMidBindingResponse updateMidBinding(Long id, MerchantChannelMidBindingSaveRequest request);

    /**
     * 更新商户与渠道 MID 绑定状态。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    MerchantChannelMidBindingResponse updateMidBindingStatus(Long id, Integer status);

    /**
     * 删除或停用渠道 MID 绑定，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteMidBinding(Long id);

}
