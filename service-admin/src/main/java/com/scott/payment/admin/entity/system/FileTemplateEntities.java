package com.scott.payment.admin.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统文件模板中心持久化模型。
 *
 * <p>模板定义、不可变版本和本地化文件资产分表保存。对象正文只进入私有对象存储，
 * 数据库不得保存文件正文，也不得向前端返回 Bucket 或 Object Key。</p>
 */
public final class FileTemplateEntities {

    private FileTemplateEntities() {
    }

    /** 模板稳定定义。 */
    @Data
    @TableName("sys_file_template")
    public static class FileTemplateDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String templateCode;
        private String ownerSystem;
        private String moduleCode;
        private String nameZh;
        private String nameEn;
        private String fileExtension;
        private String adminPermissionCode;
        private String merchantPermissionCode;
        private Integer status;
        private Long currentVersionId;
        private String lastOperationRequestId;
        private String remark;
        private Long version;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 模板不可变版本。 */
    @Data
    @TableName("sys_file_template_version")
    public static class FileTemplateVersionDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long templateId;
        private Integer versionNo;
        private String schemaVersion;
        private String requestId;
        private String releaseNote;
        private String publishStatus;
        private String publishedBy;
        private String publishReason;
        private LocalDateTime publishedTime;
        private String createBy;
        private LocalDateTime createTime;
    }

    /** 单个版本的本地化文件资产。 */
    @Data
    @TableName("sys_file_template_asset")
    public static class FileTemplateAssetDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long templateVersionId;
        private String locale;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
        private String sha256;
        private String storageProvider;
        private String bucketName;
        private String objectKey;
        private String createBy;
        private LocalDateTime createTime;
    }
}
