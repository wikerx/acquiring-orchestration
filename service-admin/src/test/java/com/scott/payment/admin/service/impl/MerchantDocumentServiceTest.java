package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.BizDocumentDO;
import com.scott.payment.admin.infrastructure.storage.ObjectStorageProperties;
import com.scott.payment.admin.infrastructure.storage.ObjectStorageService;
import com.scott.payment.admin.mapper.BizDocumentMapper;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDocumentServiceTest
 * @date : 2026-09-14 19:15
 * @email : scott_x@163.com
 * @description : 商户资料对象存储单元测试，覆盖文件魔数、完整性摘要和删除一致性
 * @status : create
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class MerchantDocumentServiceTest {

    /** 商户主档数据访问替身。 */
    @Mock
    private BaseMerchantInfoMapper merchantInfoMapper;

    /** 文件元数据访问替身。 */
    @Mock
    private BizDocumentMapper documentMapper;

    /** 对象存储 Bean 提供器替身。 */
    @Mock
    private ObjectProvider<ObjectStorageService> storageProvider;

    /** 对象存储实现替身。 */
    @Mock
    private ObjectStorageService storageService;

    /** 被测商户资料服务。 */
    private MerchantDocumentService service;

    /** 初始化 MyBatis-Plus 元数据和非敏感存储配置。 */
    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, BaseMerchantInfoDO.class);
        TableInfoHelper.initTableInfo(assistant, BizDocumentDO.class);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setEnabled(true);
        properties.setProvider("MINIO");
        properties.setBucket("merchant-documents-test");
        service = new MerchantDocumentService(merchantInfoMapper, documentMapper, storageProvider, properties);
    }

    /** PDF 文件应按魔数识别并只把私有对象定位保存到数据库。 */
    @Test
    void shouldUploadPdfAndPersistMetadata() {
        log.info("测试商户资料上传，关键输入: PDF魔数, 文件大小=9字节");
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());
        when(storageProvider.getIfAvailable()).thenReturn(storageService);
        byte[] content = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "text/plain", content);

        service.upload("MTEST0001", "business_license", file);

        verify(storageService).put(anyString(), eq("application/pdf"), eq(content));
        ArgumentCaptor<BizDocumentDO> captor = ArgumentCaptor.forClass(BizDocumentDO.class);
        verify(documentMapper).insert(captor.capture());
        assertThat(captor.getValue().getBizId()).isEqualTo("MTEST0001");
        assertThat(captor.getValue().getDocumentType()).isEqualTo("BUSINESS_LICENSE");
        assertThat(captor.getValue().getBucketName()).isEqualTo("merchant-documents-test");
        assertThat(captor.getValue().getObjectKey()).startsWith("merchants/MTEST0001/");
        assertThat(captor.getValue().getSha256()).hasSize(64);
        log.info("商户资料上传完成，结果: MIME由魔数识别，数据库仅保存元数据和私有对象键");
    }

    /** 扩展名或请求 MIME 合法但文件魔数非法时必须拒绝上传。 */
    @Test
    void shouldRejectFileWithUnsupportedMagicBytes() {
        log.info("测试商户资料魔数校验，关键输入: filename=license.pdf, 实际正文=plain text");
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());
        MockMultipartFile file = new MockMultipartFile(
                "file", "license.pdf", "application/pdf", "not-a-pdf".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.upload("MTEST0001", "BUSINESS_LICENSE", file))
                .hasMessageContaining("仅支持 PDF、JPEG 或 PNG");

        verify(storageService, never()).put(anyString(), anyString(), any());
        verify(documentMapper, never()).insert(any(BizDocumentDO.class));
        log.info("商户资料魔数校验完成，结果: 扩展名伪装文件被拒绝且无存储副作用");
    }

    /** 下载对象摘要与数据库记录不一致时必须拒绝返回文件正文。 */
    @Test
    void shouldRejectDownloadWhenSha256DoesNotMatch() {
        log.info("测试商户资料下载完整性，关键输入: 数据库摘要与对象正文不一致");
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());
        when(documentMapper.selectOne(any())).thenReturn(existingDocument());
        when(storageProvider.getIfAvailable()).thenReturn(storageService);
        when(storageService.get("merchants/MTEST0001/license.pdf"))
                .thenReturn("changed".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.download("MTEST0001", 10L))
                .hasMessageContaining("完整性校验失败");

        log.info("商户资料下载完整性完成，结果: 摘要不一致时未向控制器返回文件正文");
    }

    /** 无活动事务同步时，软删除元数据成功后应立即删除对象正文。 */
    @Test
    void shouldDeleteObjectAfterMetadataIsSoftDeleted() {
        log.info("测试商户资料删除，关键输入: documentId=10, 无活动事务同步");
        when(merchantInfoMapper.selectOne(any())).thenReturn(existingMerchant());
        when(documentMapper.selectOne(any())).thenReturn(existingDocument());
        when(documentMapper.update(eq(null), any())).thenReturn(1);
        when(storageProvider.getIfAvailable()).thenReturn(storageService);

        service.delete("MTEST0001", 10L);

        verify(storageService).delete("merchants/MTEST0001/license.pdf");
        log.info("商户资料删除完成，结果: 元数据软删除成功后对象正文已清理");
    }

    /** 构造不包含真实资料的测试商户。 */
    private BaseMerchantInfoDO existingMerchant() {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setId(1L);
        merchant.setMerchantId("MTEST0001");
        merchant.setDeleted(0);
        return merchant;
    }

    /** 构造不包含真实文件内容的测试资料元数据。 */
    private BizDocumentDO existingDocument() {
        BizDocumentDO document = new BizDocumentDO();
        document.setId(10L);
        document.setBizType("MERCHANT_KYB");
        document.setBizId("MTEST0001");
        document.setOriginalFilename("license.pdf");
        document.setContentType("application/pdf");
        document.setSha256("0".repeat(64));
        document.setObjectKey("merchants/MTEST0001/license.pdf");
        document.setDeleted(0);
        return document;
    }
}
