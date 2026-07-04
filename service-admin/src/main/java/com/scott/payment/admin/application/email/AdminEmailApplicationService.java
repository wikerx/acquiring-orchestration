package com.scott.payment.admin.application.email;

import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountSaveRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountTestRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateSaveRequest;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 管理后台邮件管理应用服务。
 *
 * <p>负责邮件管理用例编排，Controller 只处理 HTTP 映射和权限控制。</p>
 */
@Service
public class AdminEmailApplicationService {

    private final AdminEmailService adminEmailService;

    public AdminEmailApplicationService(AdminEmailService adminEmailService) {
        this.adminEmailService = adminEmailService;
    }

    public PageResult<EmailAccountResponse> pageAccounts(EmailAccountQuery query) {
        return adminEmailService.pageAccounts(query);
    }

    public EmailAccountResponse getAccount(Long id) {
        return adminEmailService.getAccount(id);
    }

    public EmailAccountResponse createAccount(EmailAccountSaveRequest request) {
        return adminEmailService.createAccount(request);
    }

    public EmailAccountResponse updateAccount(Long id, EmailAccountSaveRequest request) {
        return adminEmailService.updateAccount(id, request);
    }

    public EmailAccountResponse updateAccountStatus(Long id, Integer status) {
        return adminEmailService.updateAccountStatus(id, status);
    }

    public EmailAccountResponse setDefaultAccount(Long id) {
        return adminEmailService.setDefaultAccount(id);
    }

    public void deleteAccount(Long id) {
        adminEmailService.deleteAccount(id);
    }

    public EmailSendResult sendTestEmail(Long accountId, EmailAccountTestRequest request) {
        return adminEmailService.sendTestEmail(accountId, request);
    }

    public PageResult<EmailTemplateResponse> pageTemplates(EmailTemplateQuery query) {
        return adminEmailService.pageTemplates(query);
    }

    public EmailTemplateResponse getTemplate(Long id) {
        return adminEmailService.getTemplate(id);
    }

    public EmailTemplateResponse createTemplate(EmailTemplateSaveRequest request) {
        return adminEmailService.createTemplate(request);
    }

    public EmailTemplateResponse updateTemplate(Long id, EmailTemplateSaveRequest request) {
        return adminEmailService.updateTemplate(id, request);
    }

    public EmailTemplateResponse copyTemplate(Long id) {
        return adminEmailService.copyTemplate(id);
    }

    public EmailTemplateResponse updateTemplateStatus(Long id, Integer status) {
        return adminEmailService.updateTemplateStatus(id, status);
    }

    public void deleteTemplate(Long id) {
        adminEmailService.deleteTemplate(id);
    }

    public EmailTemplatePreviewResponse previewTemplate(EmailTemplatePreviewRequest request) {
        return adminEmailService.previewTemplate(request);
    }

    public PageResult<EmailRecordResponse> pageRecords(EmailRecordQuery query) {
        return adminEmailService.pageRecords(query);
    }

    public EmailRecordResponse getRecord(Long id) {
        return adminEmailService.getRecord(id);
    }

    public EmailSendResult sendByTemplate(EmailSendRequest request) {
        return adminEmailService.sendByTemplate(request);
    }

    public EmailSendResult resend(Long id) {
        return adminEmailService.resend(id);
    }
}
