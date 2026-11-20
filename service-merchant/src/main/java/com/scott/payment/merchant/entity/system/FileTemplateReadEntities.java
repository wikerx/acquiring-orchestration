package com.scott.payment.merchant.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 商户系统读取模板中心已发布资产所需的最小持久化投影。 */
public final class FileTemplateReadEntities {

    private FileTemplateReadEntities() {
    }

    /** 商户端只读的模板定义与当前版本指针，不承载发布或编辑操作。 */
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

    /** 商户下载所需的不可变版本元数据，只能通过定义的发布指针选取。 */
    @Data
    @TableName("sys_file_template_version")
    public static class VersionDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long templateId;
        private String publishStatus;
    }

    /** 发布版本的语言资产及完整性信息，私有对象键不得返回浏览器。 */
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
