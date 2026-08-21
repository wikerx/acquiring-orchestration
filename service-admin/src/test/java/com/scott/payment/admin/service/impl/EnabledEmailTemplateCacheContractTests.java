package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateSaveRequest;
import com.scott.payment.admin.entity.email.EmailEntities.EmailTemplateDO;
import com.scott.payment.admin.mapper.EmailAccountMapper;
import com.scott.payment.admin.mapper.EmailSendRecordMapper;
import com.scott.payment.admin.mapper.EmailTemplateMapper;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.email.model.EnabledEmailTemplateSnapshot;
import com.scott.payment.component.db.email.service.EnabledEmailTemplateCacheCondition;
import com.scott.payment.component.db.email.service.EnabledEmailTemplateCacheReader;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import com.scott.payment.component.redis.cache.PaymentCacheRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EnabledEmailTemplateCacheContractTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 管理端和商户端共享已启用邮件模板缓存的读写及序列化契约测试
 * @status : create
 */
@Slf4j
class EnabledEmailTemplateCacheContractTests {

    /** 初始化 MyBatis-Plus Lambda 查询所需邮件模板实体元数据。 */
    @BeforeEach
    void setUpTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, EmailTemplateDO.class);
    }

    /** 邮件模板缓存必须使用组合键并从主库可靠重建。 */
    @Test
    void shouldRebuildEnabledTemplateCacheFromMaster() throws Exception {
        Method method = EnabledEmailTemplateCacheReader.class.getMethod(
                "findEnabled", String.class, String.class);
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly(PaymentCacheNames.EMAIL_TEMPLATE_ENABLED);
        assertThat(cacheable.key()).contains("EmailTemplateCacheKey");
        assertThat(cacheable.condition()).contains("enabledEmailTemplateCacheCondition");
        assertThat(cacheable.unless()).isEqualTo("#result == null");
        assertThat(PaymentCacheRegistry.defaultTtls())
                .containsEntry(PaymentCacheNames.EMAIL_TEMPLATE_ENABLED, Duration.ofMinutes(5));
    }

    /** 模板变更门禁存在或状态未知时必须绕过缓存，防止旧模板再次回填。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldBypassTemplateCacheWhileInvalidationIsPendingOrUnknown() {
        log.info("测试邮件模板缓存变更门禁，关键输入: pending 与 Redis 状态未知");
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        ObjectProvider<CacheInvalidationGuard> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(invalidationGuard);
        EnabledEmailTemplateCacheCondition condition =
                new EnabledEmailTemplateCacheCondition(provider);

        when(invalidationGuard.isPending(
                PaymentCacheNames.EMAIL_TEMPLATE_ENABLED,
                "LOGIN_NOTICE:zh-CN"
        )).thenReturn(true);
        assertThat(condition.isCacheAllowed("LOGIN_NOTICE", "zh-CN")).isFalse();

        when(invalidationGuard.isPending(
                PaymentCacheNames.EMAIL_TEMPLATE_ENABLED,
                "LOGIN_NOTICE:en-US"
        )).thenThrow(new IllegalStateException("redis unavailable"));
        assertThat(condition.isCacheAllowed("LOGIN_NOTICE", "en-US")).isFalse();
        log.info("邮件模板缓存变更门禁测试完成，结果: 两种场景均绕过旧缓存");
    }

    /** 邮件模板缓存快照必须能通过受控 Redis 类型白名单往返序列化。 */
    @Test
    void shouldRoundTripEnabledTemplateSnapshotThroughRedisSerializer() {
        EnabledEmailTemplateSnapshot source = new EnabledEmailTemplateSnapshot();
        source.setId(1L);
        source.setTemplateCode("LOGIN_NOTICE");
        source.setTemplateName("Login notice");
        source.setAppCode("MERCHANT");
        source.setSceneCode("SECURITY");
        source.setLocale("zh-CN");
        source.setSubjectTemplate("Notice ${otp}");
        source.setContentType("HTML");
        source.setContentTemplate("Code ${otp}");
        source.setSensitiveVariableNames("[\"otp\"]");

        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();
        Object restored = serializer.deserialize(serializer.serialize(source));

        assertThat(restored).usingRecursiveComparison().isEqualTo(source);
    }

    /** 管理端查看已启用模板详情时应预热发信快照缓存，便于直接核验缓存链路。 */
    @Test
    void shouldWarmEnabledTemplateCacheWhenReadingTemplateDetail() {
        Fixture fixture = fixture();
        EmailTemplateDO existing = template("LOGIN_NOTICE", "zh-CN");
        when(fixture.templateMapper().selectOne(any())).thenReturn(existing);

        fixture.service().getTemplate(1L);

        verify(fixture.enabledTemplateCacheReader()).findEnabled("LOGIN_NOTICE", "zh-CN");
    }

    /** 模板新增、复制、启停和删除都必须登记各自的精确缓存失效键。 */
    @Test
    void shouldEvictExactKeysAfterTemplateCreateCopyStatusAndDelete() {
        Fixture fixture = fixture();
        EmailTemplateDO existing = template("LOGIN_NOTICE", "zh-CN");
        when(fixture.templateMapper().selectOne(any())).thenReturn(existing);

        fixture.service().createTemplate(request("LOGIN_NOTICE", "zh-CN"));
        fixture.service().copyTemplate(1L);
        fixture.service().updateTemplateStatus(1L, 0);
        fixture.service().deleteTemplate(1L);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(fixture.cacheInvalidationCoordinator(), org.mockito.Mockito.times(4)).prepare(
                org.mockito.ArgumentMatchers.eq(PaymentCacheNames.EMAIL_TEMPLATE_ENABLED),
                keys.capture());
        assertThat(keys.getAllValues()).contains("LOGIN_NOTICE:zh-CN");
        assertThat(keys.getAllValues().stream().map(String::valueOf))
                .anyMatch(key -> key.startsWith("LOGIN_NOTICE_COPY_") && key.endsWith(":zh-CN"));
    }

    /** 修改模板编码或语言时必须同时登记旧键和新键。 */
    @Test
    void shouldEvictOldAndNewKeysAfterTemplateIdentityChange() {
        Fixture fixture = fixture();
        EmailTemplateDO existing = template("LOGIN_NOTICE", "zh-CN");
        when(fixture.templateMapper().selectOne(any())).thenReturn(existing);

        fixture.service().updateTemplate(1L, request("SECURITY_NOTICE", "en-US"));

        verify(fixture.cacheInvalidationCoordinator()).prepare(
                PaymentCacheNames.EMAIL_TEMPLATE_ENABLED, "LOGIN_NOTICE:zh-CN");
        verify(fixture.cacheInvalidationCoordinator()).prepare(
                PaymentCacheNames.EMAIL_TEMPLATE_ENABLED, "SECURITY_NOTICE:en-US");
    }

    /** 所有模板写入口必须固定使用主库和回滚事务。 */
    @Test
    void shouldRunAllTemplateMutationsInsideMasterTransactions() throws Exception {
        assertMutation("createTemplate", EmailTemplateSaveRequest.class);
        assertMutation("updateTemplate", Long.class, EmailTemplateSaveRequest.class);
        assertMutation("copyTemplate", Long.class);
        assertMutation("updateTemplateStatus", Long.class, Integer.class);
        assertMutation("deleteTemplate", Long.class);
    }

    private void assertMutation(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminEmailServiceImpl.class.getMethod(methodName, parameterTypes);
        DS dataSource = method.getAnnotation(DS.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(dataSource).as(method.toString()).isNotNull();
        assertThat(dataSource.value()).as(method.toString()).isEqualTo(DataSourceName.MASTER);
        assertThat(transactional).as(method.toString()).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private Fixture fixture() {
        EmailTemplateMapper templateMapper = mock(EmailTemplateMapper.class);
        EnabledEmailTemplateCacheReader enabledTemplateCacheReader =
                mock(EnabledEmailTemplateCacheReader.class);
        ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator =
                mock(ManagedCacheInvalidationCoordinator.class);
        AdminConfigService configService = mock(AdminConfigService.class);
        when(configService.enabledConfigValues(any())).thenReturn(Map.of());
        AdminEmailServiceImpl service = new AdminEmailServiceImpl(
                mock(EmailAccountMapper.class),
                templateMapper,
                mock(EmailSendRecordMapper.class),
                configService,
                mock(EmailPayloadCrypto.class),
                mock(AdminEmailDeliveryService.class),
                mock(AdminSmtpEmailSender.class),
                new EmailDeliveryProperties(),
                enabledTemplateCacheReader,
                cacheInvalidationCoordinator
        );
        return new Fixture(service, templateMapper, enabledTemplateCacheReader,
                cacheInvalidationCoordinator);
    }

    private EmailTemplateSaveRequest request(String code, String locale) {
        EmailTemplateSaveRequest request = new EmailTemplateSaveRequest();
        request.setTemplateCode(code);
        request.setTemplateName("Security notice");
        request.setAppCode("MERCHANT");
        request.setSceneCode("SECURITY");
        request.setLocale(locale);
        request.setSubjectTemplate("Notice ${otp}");
        request.setContentType("HTML");
        request.setContentTemplate("Code ${otp}");
        request.setSensitiveVariableNames("[\"otp\"]");
        request.setStatus(1);
        return request;
    }

    private EmailTemplateDO template(String code, String locale) {
        EmailTemplateDO row = new EmailTemplateDO();
        row.setId(1L);
        row.setTemplateCode(code);
        row.setTemplateName("Security notice");
        row.setAppCode("MERCHANT");
        row.setSceneCode("SECURITY");
        row.setLocale(locale);
        row.setSubjectTemplate("Notice ${otp}");
        row.setContentType("HTML");
        row.setContentTemplate("Code ${otp}");
        row.setSensitiveVariableNames("[\"otp\"]");
        row.setStatus(1);
        row.setSystemBuiltin(0);
        row.setVersionNo(1);
        row.setDeleted(0L);
        return row;
    }

    private record Fixture(AdminEmailServiceImpl service,
                           EmailTemplateMapper templateMapper,
                           EnabledEmailTemplateCacheReader enabledTemplateCacheReader,
                           ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator) {
    }
}
