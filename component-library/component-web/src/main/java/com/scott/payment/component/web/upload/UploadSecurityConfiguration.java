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
    /** 未提供扫描实现时注册 ClamAV；基础设施不可用仅在上传时拒绝，不绕过扫描。 */
    @Bean @ConditionalOnMissingBean(MalwareScanner.class)
    public ClamAvMalwareScanner malwareScanner(UploadSecurityProperties properties) { return new ClamAvMalwareScanner(properties); }
    /** 组装格式、大小与扫描的统一上传入口，业务仍须先做对象授权。 */
    @Bean @ConditionalOnMissingBean
    public UploadSecurityService uploadSecurityService(MalwareScanner scanner, UploadSecurityProperties properties) { return new UploadSecurityService(scanner, properties); }
    /** 复用统一上传校验及扫描器，限制图片解码并扫描重编码结果。 */
    @Bean @ConditionalOnMissingBean
    public SafeImageProcessor safeImageProcessor(UploadSecurityService uploads, MalwareScanner scanner) { return new SafeImageProcessor(uploads, scanner); }
    /** 延迟取得私有存储端口，使未启用图片设施不影响无关服务启动。 */
    @Bean @ConditionalOnMissingBean
    public ManagedImageStore managedImageStore(ObjectProvider<ImageObjectStorage> storage) { return new ManagedImageStore(storage); }
}
