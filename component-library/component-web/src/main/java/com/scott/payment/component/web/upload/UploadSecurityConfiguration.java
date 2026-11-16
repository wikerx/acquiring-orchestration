package com.scott.payment.component.web.upload;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅在调用上传功能时依赖扫描器；扫描设施缺失时拒绝上传，不阻止其他业务启动。 */
@Configuration
@EnableConfigurationProperties(UploadSecurityProperties.class)
public class UploadSecurityConfiguration {
    @Bean @ConditionalOnMissingBean(MalwareScanner.class)
    public ClamAvMalwareScanner malwareScanner(UploadSecurityProperties properties) { return new ClamAvMalwareScanner(properties); }
    @Bean @ConditionalOnMissingBean
    public UploadSecurityService uploadSecurityService(MalwareScanner scanner, UploadSecurityProperties properties) { return new UploadSecurityService(scanner, properties); }
    @Bean @ConditionalOnMissingBean
    public SafeImageProcessor safeImageProcessor(UploadSecurityService uploads, MalwareScanner scanner) { return new SafeImageProcessor(uploads, scanner); }
    @Bean @ConditionalOnMissingBean
    public ManagedImageStore managedImageStore(ObjectProvider<ImageObjectStorage> storage) { return new ManagedImageStore(storage); }
}
