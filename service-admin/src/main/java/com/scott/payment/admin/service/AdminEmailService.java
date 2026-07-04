package com.scott.payment.admin.service;

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
import com.scott.payment.component.core.model.PageResult;

/**
 * 管理后台邮件管理服务。
 */
public interface AdminEmailService {

    PageResult<EmailAccountResponse> pageAccounts(EmailAccountQuery query);

    EmailAccountResponse getAccount(Long id);

    EmailAccountResponse createAccount(EmailAccountSaveRequest request);

    EmailAccountResponse updateAccount(Long id, EmailAccountSaveRequest request);

    EmailAccountResponse updateAccountStatus(Long id, Integer status);

    EmailAccountResponse setDefaultAccount(Long id);

    void deleteAccount(Long id);

    EmailSendResult sendTestEmail(Long accountId, EmailAccountTestRequest request);

    PageResult<EmailTemplateResponse> pageTemplates(EmailTemplateQuery query);

    EmailTemplateResponse getTemplate(Long id);

    EmailTemplateResponse createTemplate(EmailTemplateSaveRequest request);

    EmailTemplateResponse updateTemplate(Long id, EmailTemplateSaveRequest request);

    EmailTemplateResponse copyTemplate(Long id);

    EmailTemplateResponse updateTemplateStatus(Long id, Integer status);

    void deleteTemplate(Long id);

    EmailTemplatePreviewResponse previewTemplate(EmailTemplatePreviewRequest request);

    PageResult<EmailRecordResponse> pageRecords(EmailRecordQuery query);

    EmailRecordResponse getRecord(Long id);

    EmailSendResult sendByTemplate(EmailSendRequest request);

    EmailSendResult resend(Long id);
}
