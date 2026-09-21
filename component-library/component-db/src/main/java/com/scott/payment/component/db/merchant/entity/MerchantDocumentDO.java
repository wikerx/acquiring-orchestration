package com.scott.payment.component.db.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDocumentDO
 * @date : 2026-09-21 09:20
 * @email : scott_x@163.com
 * @description : 商户合规资料共享元数据实体，文件正文存放在私有对象存储
 * @status : create
 */
@Data
@TableName("biz_document")
public class MerchantDocumentDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizId;
    private String requestNo;
    private String documentType;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String storageProvider;
    private String bucketName;
    private String objectKey;
    private String documentStatus;
    private String uploadedBy;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
    private Integer deleted;
}
