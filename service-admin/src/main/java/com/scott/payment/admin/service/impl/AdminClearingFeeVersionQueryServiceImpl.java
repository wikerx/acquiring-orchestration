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

/** 从管理库副本读取清分重算所需的最小费用版本描述。 */
@Service
public class AdminClearingFeeVersionQueryServiceImpl implements AdminClearingFeeVersionQueryService {

    private static final long NOT_DELETED = 0L;
    private static final Set<String> IMMUTABLE_STATUSES = Set.of("ACTIVE", "SUPERSEDED");

    private final FeePlanMapper planMapper;
    private final FeePlanVersionMapper versionMapper;

    public AdminClearingFeeVersionQueryServiceImpl(FeePlanMapper planMapper,
                                                   FeePlanVersionMapper versionMapper) {
        this.planMapper = planMapper;
        this.versionMapper = versionMapper;
    }

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
