package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.support.MerchantLocaleSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies session and notification side effects for merchant freeze state transitions. */
@Slf4j
@Service
public class AdminMerchantStatusLifecycleService {

    public static final String TEMPLATE_FROZEN = "MERCHANT_FROZEN";
    public static final String TEMPLATE_UNFROZEN = "MERCHANT_UNFROZEN";
    private static final String SCENE = "MERCHANT_STATUS_CHANGED";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysAppMapper appMapper;
    private final SysAccountMapper accountMapper;
    private final SysLoginSessionMapper loginSessionMapper;
    private final SysRoleMapper roleMapper;
    private final SysMerchantUserRoleMapper merchantUserRoleMapper;
    private final SysMerchantUserMapper merchantUserMapper;
    private final AdminEmailService emailService;

    public AdminMerchantStatusLifecycleService(SysAppMapper appMapper,
                                               SysAccountMapper accountMapper,
                                               SysLoginSessionMapper loginSessionMapper,
                                               SysRoleMapper roleMapper,
                                               SysMerchantUserRoleMapper merchantUserRoleMapper,
                                               SysMerchantUserMapper merchantUserMapper,
                                               AdminEmailService emailService) {
        this.appMapper = appMapper;
        this.accountMapper = accountMapper;
        this.loginSessionMapper = loginSessionMapper;
        this.roleMapper = roleMapper;
        this.merchantUserRoleMapper = merchantUserRoleMapper;
        this.merchantUserMapper = merchantUserMapper;
        this.emailService = emailService;
    }

    public void onStatusChanged(BaseMerchantInfoDO merchant, int targetStatus, LocalDateTime operationTime) {
        SysAppDO app = merchantApp();
        List<SysAccountDO> merchantAccounts = activeMerchantAccounts(app.getId(), merchant.getMerchantId());
        if (targetStatus == 2) {
            logoutSessions(app.getId(), merchantAccounts, operationTime);
        }
        List<String> recipients = administratorEmails(app.getId(), merchant, merchantAccounts);
        String templateCode = targetStatus == 2 ? TEMPLATE_FROZEN : TEMPLATE_UNFROZEN;
        sendAfterCommit(merchant, recipients, templateCode, operationTime);
    }

    private SysAppDO merchantApp() {
        SysAppDO app = appMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, AuthConstants.APP_MERCHANT)
                .eq(SysAppDO::getStatus, AuthConstants.ENABLED)
                .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new IllegalStateException("merchant application is unavailable");
        }
        return app;
    }

    private List<SysAccountDO> activeMerchantAccounts(Long appId, String merchantId) {
        return accountMapper.selectList(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getAppId, appId)
                .eq(SysAccountDO::getMerchantId, merchantId)
                .eq(SysAccountDO::getStatus, AuthConstants.ENABLED)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED));
    }

    private void logoutSessions(Long appId, List<SysAccountDO> accounts, LocalDateTime now) {
        List<Long> accountIds = accounts.stream().map(SysAccountDO::getId).filter(java.util.Objects::nonNull).toList();
        if (accountIds.isEmpty()) {
            return;
        }
        loginSessionMapper.update(Wrappers.<SysLoginSessionDO>lambdaUpdate()
                .set(SysLoginSessionDO::getLogout, AuthConstants.ENABLED)
                .set(SysLoginSessionDO::getLogoutAt, now)
                .set(SysLoginSessionDO::getUpdatedAt, now)
                .eq(SysLoginSessionDO::getAppId, appId)
                .in(SysLoginSessionDO::getAccountId, accountIds)
                .eq(SysLoginSessionDO::getLogout, AuthConstants.DISABLED));
    }

    private List<String> administratorEmails(Long appId,
                                             BaseMerchantInfoDO merchant,
                                             List<SysAccountDO> merchantAccounts) {
        SysRoleDO adminRole = roleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getRoleCode, AuthConstants.DEFAULT_MERCHANT_ROLE + "_" + merchant.getMerchantId())
                .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (adminRole != null) {
            List<Long> merchantUserIds = merchantUserRoleMapper.selectList(
                            Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                                    .eq(SysMerchantUserRoleDO::getAppId, appId)
                                    .eq(SysMerchantUserRoleDO::getRoleId, adminRole.getId())
                                    .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED))
                    .stream().map(SysMerchantUserRoleDO::getMerchantUserId).filter(java.util.Objects::nonNull).toList();
            if (!merchantUserIds.isEmpty()) {
                List<Long> adminAccountIds = merchantUserMapper.selectList(Wrappers.<SysMerchantUserDO>lambdaQuery()
                                .in(SysMerchantUserDO::getId, merchantUserIds)
                                .eq(SysMerchantUserDO::getMerchantId, merchant.getMerchantId())
                                .eq(SysMerchantUserDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED))
                        .stream().map(SysMerchantUserDO::getAccountId).filter(java.util.Objects::nonNull).toList();
                List<String> adminEmails = merchantAccounts.stream()
                        .filter(account -> adminAccountIds.contains(account.getId()))
                        .map(SysAccountDO::getEmail).filter(StringUtils::hasText).map(String::trim).distinct().toList();
                if (!adminEmails.isEmpty()) {
                    return adminEmails;
                }
            }
        }
        return StringUtils.hasText(merchant.getContactEmail())
                ? List.of(merchant.getContactEmail().trim()) : List.of();
    }

    private void sendAfterCommit(BaseMerchantInfoDO merchant,
                                 List<String> recipients,
                                 String templateCode,
                                 LocalDateTime operationTime) {
        if (recipients.isEmpty()) {
            return;
        }
        Runnable task = () -> send(merchant, recipients, templateCode, operationTime);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private void send(BaseMerchantInfoDO merchant,
                      List<String> recipients,
                      String templateCode,
                      LocalDateTime operationTime) {
        try {
            EmailSendRequest request = new EmailSendRequest();
            request.setAppCode(AuthConstants.APP_MERCHANT);
            request.setMerchantId(merchant.getMerchantId());
            request.setMerchantNo(merchant.getMerchantId());
            request.setMerchantName(merchant.getMerchantName());
            request.setTemplateCode(templateCode);
            request.setSceneCode(SCENE);
            request.setLocale(MerchantLocaleSupport.normalize(merchant.getDefaultLocale()));
            request.setToEmails(recipients);
            request.setBizType(SCENE);
            request.setBizNo(merchant.getMerchantId());
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("systemName", "Vexra Merchant");
            variables.put("merchantName", merchant.getMerchantName());
            variables.put("merchantId", merchant.getMerchantId());
            variables.put("operatorName", operatorName());
            variables.put("operationTime", TIME_FORMATTER.format(operationTime));
            request.setVariables(variables);
            emailService.sendByTemplate(request);
        } catch (RuntimeException exception) {
            log.warn("merchant status notice send failed, merchantId: {}, templateCode: {}, exceptionType: {}",
                    merchant.getMerchantId(), templateCode, exception.getClass().getSimpleName());
        }
    }

    private String operatorName() {
        InternalAuthAccount operator = InternalAuthContextHolder.get();
        if (operator == null) {
            return "System Administrator";
        }
        if (StringUtils.hasText(operator.getRealName())) {
            return operator.getRealName();
        }
        return StringUtils.hasText(operator.getLoginAccount()) ? operator.getLoginAccount() : "System Administrator";
    }
}
