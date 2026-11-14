package com.scott.payment.merchant.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 商户系统读取模板中心已发布资产所需的最小持久化投影。 */
public final class FileTemplateReadEntities {

    private FileTemplateReadEntities() {
    }

    @Data
    @TableName("sys_file_template")
    public static class TemplateDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String templateCode;
        private String ownerSystem;
        private String nameZh;
        private String nameEn;
        private String fileExtension;
        private String merchantPermissionCode;
        private Integer status;
        private Long currentVersionId;
        private Long deleted;
    }

    @Data
    @TableName("sys_file_template_version")
    public static class VersionDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long templateId;
        private String publishStatus;
    }

    @Data
    @TableName("sys_file_template_asset")
    public static class AssetDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long templateVersionId;
        private String locale;
        private String contentType;
        private String sha256;
        private String objectKey;
    }
}
