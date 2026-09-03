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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelApplicationService
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : admin渠道应用服务，位于 运营后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class AdminChannelApplicationService {

    private final AdminChannelService adminChannelService;

    public AdminChannelApplicationService(AdminChannelService adminChannelService) {
        this.adminChannelService = adminChannelService;
    }

    /**
     * 查询渠道；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery query) {
        return adminChannelService.pageChannels(query);
    }

    /**
     * 查询渠道选项；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public List<ChannelOption> listChannelOptions() {
        return adminChannelService.listChannelOptions();
    }

    /**
     * 查询渠道；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public ChannelInfoResponse getChannel(Long id) {
        return adminChannelService.getChannel(id);
    }

    /**
     * 创建渠道，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelInfoResponse createChannel(ChannelInfoSaveRequest request) {
        return adminChannelService.createChannel(request);
    }

    /**
     * 更新渠道，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request) {
        return adminChannelService.updateChannel(id, request);
    }

    /**
     * 更新渠道状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelInfoResponse updateChannelStatus(Long id, Integer status) {
        return adminChannelService.updateChannelStatus(id, status);
    }

    /**
     * 删除或停用渠道，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteChannel(Long id) {
        adminChannelService.deleteChannel(id);
    }

    /**
     * 查询渠道能力；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery query) {
        return adminChannelService.pageCapabilities(query);
    }

    /**
     * 查询渠道能力；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public CapabilityResponse getCapability(Long id) {
        return adminChannelService.getCapability(id);
    }

    /**
     * 创建渠道能力，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public CapabilityResponse createCapability(CapabilitySaveRequest request) {
        return adminChannelService.createCapability(request);
    }

    /**
     * 更新渠道能力，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request) {
        return adminChannelService.updateCapability(id, request);
    }

    /**
     * 更新渠道能力状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    public CapabilityResponse updateCapabilityStatus(Long id, Integer status) {
        return adminChannelService.updateCapabilityStatus(id, status);
    }

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
    public CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization) {
        return adminChannelService.updateCapabilitySupport(id, support3ds, supportIncrementalAuthorization);
    }

    /**
     * 删除或停用渠道能力，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteCapability(Long id) {
        adminChannelService.deleteCapability(id);
    }

    /**
     * 查询限额；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<LimitResponse> pageLimits(LimitQuery query) {
        return adminChannelService.pageLimits(query);
    }

    /**
     * 查询限额；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public LimitResponse getLimit(Long id) {
        return adminChannelService.getLimit(id);
    }

    /**
     * 创建限额，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public LimitResponse createLimit(LimitSaveRequest request) {
        return adminChannelService.createLimit(request);
    }

    /**
     * 创建限额，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public List<LimitResponse> createLimits(LimitBatchSaveRequest request) {
        return adminChannelService.createLimits(request);
    }

    /**
     * 创建限额维度，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request) {
        return adminChannelService.saveLimitDimension(request);
    }

    /**
     * 更新限额，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public LimitResponse updateLimit(Long id, LimitSaveRequest request) {
        return adminChannelService.updateLimit(id, request);
    }

    /**
     * 更新渠道限额规则状态。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    public LimitResponse updateLimitStatus(Long id, Integer status) {
        return adminChannelService.updateLimitStatus(id, status);
    }

    /**
     * 删除或停用限额，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteLimit(Long id) {
        adminChannelService.deleteLimit(id);
    }

    /**
     * 查询{@code pageMids}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<ChannelMidConfigResponse> pageMids(ChannelMidConfigQuery query) {
        return adminChannelService.pageMids(query);
    }

    /**
     * 查询{@code getMid}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public ChannelMidConfigResponse getMid(Long id) {
        return adminChannelService.getMid(id);
    }

    /**
     * 创建{@code createMid}，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelMidConfigResponse createMid(ChannelMidConfigSaveRequest request) {
        return adminChannelService.createMid(request);
    }

    /**
     * 更新渠道 MID，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelMidConfigResponse updateMid(Long id, ChannelMidConfigSaveRequest request) {
        return adminChannelService.updateMid(id, request);
    }

    /**
     * 更新渠道 MID 配置状态。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    public ChannelMidConfigResponse updateMidStatus(Long id, Integer status) {
        return adminChannelService.updateMidStatus(id, status);
    }

    /**
     * 删除或停用渠道 MID，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteMid(Long id) {
        adminChannelService.deleteMid(id);
    }

    /**
     * 查询{@code pageMidBindings}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public PageResult<MerchantChannelMidBindingResponse> pageMidBindings(MerchantChannelMidBindingQuery query) {
        return adminChannelService.pageMidBindings(query);
    }

    /**
     * 查询{@code getMidBinding}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public MerchantChannelMidBindingResponse getMidBinding(Long id) {
        return adminChannelService.getMidBinding(id);
    }

    /**
     * 创建{@code createMidBinding}，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 运营后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public MerchantChannelMidBindingResponse createMidBinding(MerchantChannelMidBindingSaveRequest request) {
        return adminChannelService.createMidBinding(request);
    }

    /**
     * 更新渠道 MID 绑定，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    public MerchantChannelMidBindingResponse updateMidBinding(Long id, MerchantChannelMidBindingSaveRequest request) {
        return adminChannelService.updateMidBinding(id, request);
    }

    /**
     * 更新商户与渠道 MID 绑定状态。
     * <p>
     * 状态或配置变更必须通过 运营后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    public MerchantChannelMidBindingResponse updateMidBindingStatus(Long id, Integer status) {
        return adminChannelService.updateMidBindingStatus(id, status);
    }

    /**
     * 删除或停用渠道 MID 绑定，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 运营后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    public void deleteMidBinding(Long id) {
        adminChannelService.deleteMidBinding(id);
    }

}
