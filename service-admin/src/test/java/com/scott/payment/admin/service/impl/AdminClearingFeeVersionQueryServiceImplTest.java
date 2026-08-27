package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculationOptionsResponse;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanVersionDO;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.FeePlanVersionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminClearingFeeVersionQueryServiceImplTest {

    @Test
    void listOptionsShouldExposeOnlyImmutableVersionsForTheMerchantPlan() {
        FeePlanMapper planMapper = mock(FeePlanMapper.class);
        FeePlanVersionMapper versionMapper = mock(FeePlanVersionMapper.class);
        FeePlanDO plan = new FeePlanDO();
        plan.setId(20L);
        plan.setMerchantId("M-1");
        plan.setPlanType("MERCHANT");
        plan.setPlanCode("FM-001");
        plan.setPlanName("Merchant standard fees");
        plan.setCurrentVersionId(22L);
        when(planMapper.selectOne(any())).thenReturn(plan);
        when(versionMapper.selectList(any())).thenReturn(List.of(
                version(23L, 3, "DRAFT"),
                version(22L, 2, "ACTIVE"),
                version(21L, 1, "SUPERSEDED")));
        AdminClearingFeeVersionQueryServiceImpl service =
                new AdminClearingFeeVersionQueryServiceImpl(planMapper, versionMapper);

        RecalculationOptionsResponse response = service.listOptions(" M-1 ", 20L);

        assertThat(response.getMerchantId()).isEqualTo("M-1");
        assertThat(response.getFeePlanId()).isEqualTo(20L);
        assertThat(response.getPlanCode()).isEqualTo("FM-001");
        assertThat(response.getPlanName()).isEqualTo("Merchant standard fees");
        assertThat(response.getCurrentVersionId()).isEqualTo(22L);
        assertThat(response.getVersions()).extracting("versionId")
                .containsExactly(22L, 21L);
        assertThat(response.getVersions()).extracting("versionStatus")
                .containsExactly("ACTIVE", "SUPERSEDED");
    }

    private FeePlanVersionDO version(Long id, int number, String status) {
        FeePlanVersionDO version = new FeePlanVersionDO();
        version.setId(id);
        version.setPlanId(20L);
        version.setVersionNo(number);
        version.setVersionStatus(status);
        return version;
    }
}
