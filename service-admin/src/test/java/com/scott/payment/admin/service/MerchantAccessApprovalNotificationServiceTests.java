package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.support.approval.MerchantAccessApprovalStatus;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 商户访问配置审批邮件的事务时机和失败隔离测试。
 */
class MerchantAccessApprovalNotificationServiceTests {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldSendApprovedEmailOnlyAfterTransactionCommit() {
        AdminEmailService emailService = mock(AdminEmailService.class);
        MerchantAccessApprovalNotificationService service =
                new MerchantAccessApprovalNotificationService(emailService);
        TransactionSynchronizationManager.initSynchronization();

        service.sendAfterCommit(
                merchant(),
                MerchantAccessApprovalNotificationService.TYPE_SOURCE_URL,
                "https://shop.example.com",
                MerchantAccessApprovalStatus.APPROVED,
                1,
                null,
                LocalDateTime.of(2026, 8, 6, 10, 0)
        );

        verify(emailService, never()).sendByTemplate(org.mockito.ArgumentMatchers.any());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTemplateCode()).isEqualTo("MERCHANT_SOURCE_URL_APPROVED");
        assertThat(requestCaptor.getValue().getVariables())
                .containsEntry("transactionStatusText", "允许交易")
                .containsEntry("configValue", "https://shop.example.com");
    }

    @Test
    void shouldKeepApprovalSuccessfulWhenRejectedEmailFails() {
        AdminEmailService emailService = mock(AdminEmailService.class);
        doThrow(new IllegalStateException("smtp unavailable"))
                .when(emailService).sendByTemplate(org.mockito.ArgumentMatchers.any());
        MerchantAccessApprovalNotificationService service =
                new MerchantAccessApprovalNotificationService(emailService);

        assertThatCode(() -> service.sendAfterCommit(
                merchant(),
                MerchantAccessApprovalNotificationService.TYPE_IP_WHITELIST,
                "198.51.100.24",
                MerchantAccessApprovalStatus.REJECTED,
                0,
                "IP ownership could not be verified",
                LocalDateTime.of(2026, 8, 6, 10, 30)
        )).doesNotThrowAnyException();

        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTemplateCode()).isEqualTo("MERCHANT_IP_WHITELIST_REJECTED");
        assertThat(requestCaptor.getValue().getVariables())
                .containsEntry("transactionStatusText", "禁止交易")
                .containsEntry("rejectReason", "IP ownership could not be verified");
    }

    private BaseMerchantInfoDO merchant() {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("M10000001");
        merchant.setMerchantName("Example Merchant");
        merchant.setContactEmail("ops@example.com");
        merchant.setDefaultLocale("zh-CN");
        return merchant;
    }
}
