package com.scott.payment.component.db.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户合规资料共享元数据实体，文件正文存放在私有对象存储
 */
@Data
@TableName("biz_document")
public class MerchantDocumentDO {


    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * {@code bizType}，用于区分 {@code MerchantDocumentDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String bizType;

    private String bizId;
    /**
     * 持久化的请求编号，用于还原当前记录的业务事实。
     */
    private String requestNo;
    /**
     * {@code documentType}，用于区分 {@code MerchantDocumentDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String documentType;

    private String originalFilename;
    /**
     * 内容类型，用于区分文本、HTML、附件、JSON 或导出文件等载荷格式。
     */
    private String contentType;

    private Long fileSize;
    /**
     * 持久化的{@code sha256}，用于还原当前记录的业务事实。
     */
    private String sha256;
    /**
     * 持久化的{@code storageProvider}，用于还原当前记录的业务事实。
     */
    private String storageProvider;

    private String bucketName;
    /**
     * 持久化的{@code objectKey}，用于还原当前记录的业务事实。
     */
    private String objectKey;

    private String documentStatus;
    /**
     * 持久化的{@code uploadedBy}，用于还原当前记录的业务事实。
     */
    private String uploadedBy;
    /**
     * 持久化的{@code gmtCreate}，用于还原当前记录的业务事实。
     */
    private LocalDateTime gmtCreate;
    /**
     * 持久化的{@code gmtModified}，用于还原当前记录的业务事实。
     */
    private LocalDateTime gmtModified;
    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Integer deleted;
}
