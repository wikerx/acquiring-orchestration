package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateQuery;
import com.scott.payment.admin.entity.email.EmailEntities.EmailTemplateDO;
import com.scott.payment.admin.mapper.EmailAccountMapper;
import com.scott.payment.admin.mapper.EmailSendRecordMapper;
import com.scott.payment.admin.mapper.EmailTemplateMapper;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.db.email.service.EnabledEmailTemplateCacheReader;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailTemplatePaginationTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证邮件模板分页使用稳定排序，更新时间相同时不会跨页重复或遗漏。
 * @status : create
 */
class AdminEmailTemplatePaginationTests {

    @Test
    void shouldUseTemplateIdAsPaginationOrderTieBreaker() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, EmailTemplateDO.class);

        EmailAccountMapper accountMapper = mock(EmailAccountMapper.class);
        EmailTemplateMapper templateMapper = mock(EmailTemplateMapper.class);
        EmailSendRecordMapper recordMapper = mock(EmailSendRecordMapper.class);
        AdminConfigService configService = mock(AdminConfigService.class);
        when(configService.enabledConfigValues(any())).thenReturn(Map.of());

        AtomicReference<LambdaQueryWrapper<EmailTemplateDO>> capturedWrapper = new AtomicReference<>();
        doAnswer(invocation -> {
            Page<EmailTemplateDO> page = invocation.getArgument(0);
            capturedWrapper.set(invocation.getArgument(1));
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        }).when(templateMapper).selectPage(any(), any());

        AdminEmailServiceImpl service = new AdminEmailServiceImpl(
                accountMapper,
                templateMapper,
                recordMapper,
                configService,
                mock(EmailPayloadCrypto.class),
                mock(AdminEmailDeliveryService.class),
                mock(AdminSmtpEmailSender.class),
                new EmailDeliveryProperties(),
                mock(EnabledEmailTemplateCacheReader.class),
                mock(com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator.class)
        );

        service.pageTemplates(new EmailTemplateQuery());

        assertThat(capturedWrapper.get()).isNotNull();
        assertThat(capturedWrapper.get().getSqlSegment())
                .containsIgnoringCase("ORDER BY update_time DESC,id DESC");
    }
}
