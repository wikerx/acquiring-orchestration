package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculationOptionsResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculationVersionOption;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanVersionDO;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.FeePlanVersionMapper;
import com.scott.payment.admin.service.AdminClearingFeeVersionQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminClearingFeeVersionQueryServiceImpl
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 从管理库副本读取清分重算所需的最小费用版本描述。
 * @status : create
 */
@Service
public class AdminClearingFeeVersionQueryServiceImpl implements AdminClearingFeeVersionQueryService {

    /**
     * {@code NOT_DELETED}常量，统一 {@code AdminClearingFeeVersionQueryServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    private static final Set<String> IMMUTABLE_STATUSES = Set.of("ACTIVE", "SUPERSEDED");

    private final FeePlanMapper planMapper;
    private final FeePlanVersionMapper versionMapper;

    public AdminClearingFeeVersionQueryServiceImpl(FeePlanMapper planMapper,
                                                   FeePlanVersionMapper versionMapper) {
        this.planMapper = planMapper;
        this.versionMapper = versionMapper;
    }

    /**
     * 查询选项；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param merchantId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param feePlanId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RecalculationOptionsResponse listOptions(String merchantId, Long feePlanId) {
        if (!StringUtils.hasText(merchantId) || feePlanId == null || feePlanId < 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String normalizedMerchantId = merchantId.trim();
        FeePlanDO plan = planMapper.selectOne(Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getId, feePlanId)
                .eq(FeePlanDO::getPlanType, "MERCHANT")
                .eq(FeePlanDO::getMerchantId, normalizedMerchantId)
                .eq(FeePlanDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (plan == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        List<FeePlanVersionDO> versions = versionMapper.selectList(
                Wrappers.<FeePlanVersionDO>lambdaQuery()
                        .eq(FeePlanVersionDO::getPlanId, plan.getId())
                        .in(FeePlanVersionDO::getVersionStatus, IMMUTABLE_STATUSES)
                        .eq(FeePlanVersionDO::getDeleted, NOT_DELETED)
                        .orderByDesc(FeePlanVersionDO::getVersionNo));

        RecalculationOptionsResponse response = new RecalculationOptionsResponse();
        response.setMerchantId(normalizedMerchantId);
        response.setFeePlanId(plan.getId());
        response.setPlanCode(plan.getPlanCode());
        response.setPlanName(plan.getPlanName());
        response.setCurrentVersionId(plan.getCurrentVersionId());
        response.setVersions(versions == null ? List.of() : versions.stream()
                .filter(version -> IMMUTABLE_STATUSES.contains(version.getVersionStatus()))
                .map(this::toOption)
                .toList());
        return response;
    }

    private RecalculationVersionOption toOption(FeePlanVersionDO version) {
        RecalculationVersionOption option = new RecalculationVersionOption();
        option.setVersionId(version.getId());
        option.setVersionNo(version.getVersionNo());
        option.setVersionStatus(version.getVersionStatus());
        return option;
    }
}
