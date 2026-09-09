package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.application.risk.cache.RiskRuleCacheInvalidationCoordinator;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistApprovalRequest;
import com.scott.payment.admin.service.MerchantAccessApprovalNotificationService;
import com.scott.payment.admin.support.approval.MerchantAccessApprovalStatus;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.MerchantIpWhitelistDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.MerchantIpWhitelistMapper;
import com.scott.payment.component.db.auth.mapper.MerchantOpenApiAccessConfigMapper;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantIpWhitelistServiceImplTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户 IP 白名单审批状态、CAS 更新和通知参数测试。
 * @status : create
 */
class AdminMerchantIpWhitelistServiceImplTests {

    @BeforeEach
    void initializeTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, MerchantIpWhitelistDO.class);
    }

    @AfterEach
    void clearAuthContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void shouldApprovePendingIpWithAllowedTransactionByDefault() {
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        MerchantOpenApiAccessConfigMapper configMapper = mock(MerchantOpenApiAccessConfigMapper.class);
        BaseMerchantInfoMapper merchantMapper = mock(BaseMerchantInfoMapper.class);
        MerchantAccessApprovalNotificationService notificationService =
                mock(MerchantAccessApprovalNotificationService.class);
        AdminMerchantIpWhitelistServiceImpl service = new AdminMerchantIpWhitelistServiceImpl(
                whitelistMapper,
                configMapper,
                merchantMapper,
                mock(RiskRuleCacheInvalidationCoordinator.class),
                mock(ManagedCacheInvalidationCoordinator.class),
                notificationService
        );
        MerchantIpWhitelistDO pending = whitelist(0, 0);
        MerchantIpWhitelistDO approved = whitelist(1, 1);
        when(whitelistMapper.selectOne(any())).thenReturn(pending, approved);
        when(whitelistMapper.update(isNull(), any())).thenReturn(1);
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("M1001");
        when(merchantMapper.selectOne(any())).thenReturn(merchant);
        InternalAuthAccount operator = new InternalAuthAccount();
        operator.setRealName("Risk Reviewer");
        InternalAuthContextHolder.set(operator);
        MerchantIpWhitelistApprovalRequest request = new MerchantIpWhitelistApprovalRequest();
        request.setApprovalStatus(1);

        var response = service.approveWhitelist(21L, request);

        assertThat(response.getApprovalStatus()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(1);
        verify(notificationService).sendAfterCommit(
                eq(merchant),
                eq(MerchantAccessApprovalNotificationService.TYPE_IP_WHITELIST),
                eq("198.51.100.24"),
                eq(MerchantAccessApprovalStatus.APPROVED),
                eq(1),
                isNull(),
                any(LocalDateTime.class)
        );
    }

    @Test
    void shouldRequireReasonWhenRejectingIpWhitelist() {
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        AdminMerchantIpWhitelistServiceImpl service = new AdminMerchantIpWhitelistServiceImpl(
                whitelistMapper,
                mock(MerchantOpenApiAccessConfigMapper.class),
                mock(BaseMerchantInfoMapper.class),
                mock(RiskRuleCacheInvalidationCoordinator.class),
                mock(ManagedCacheInvalidationCoordinator.class),
                mock(MerchantAccessApprovalNotificationService.class)
        );
        MerchantIpWhitelistApprovalRequest request = new MerchantIpWhitelistApprovalRequest();
        request.setApprovalStatus(2);

        assertThatThrownBy(() -> service.approveWhitelist(21L, request))
                .isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class)
                .hasMessageContaining("拒绝原因");
        verify(whitelistMapper, never()).update(any(), any());
    }

    private MerchantIpWhitelistDO whitelist(int approvalStatus, int status) {
        MerchantIpWhitelistDO row = new MerchantIpWhitelistDO();
        row.setId(21L);
        row.setMerchantId("M1001");
        row.setIpType("IPv4");
        row.setIpValue("198.51.100.24");
        row.setApprovalStatus(approvalStatus);
        row.setStatus(status);
        row.setDeleted(0L);
        return row;
    }
}
