package com.scott.payment.component.db.auth.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyMetadata
 * @date : 2026-08-01 15:05
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 当前密钥版本的非敏感 Redis 快照，用 revision 驱动服务实例内短时密钥缓存切换
 * @status : create
 *
 * <p>本模型禁止增加 JWT Secret、RSA 私钥、公钥正文或可还原密钥的信息。数据库仍是密钥事实源，
 * Redis 只用于判断 OpenAPI 实例内的短时密钥材料是否仍对应当前版本。</p>
 */
@Data
public class MerchantKeyMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商户号，非空且作为 Redis 业务键。 */
    private String merchantId;

    /** 当前启用 JWT 密钥记录主键；未配置时允许为空。 */
    private Long jwtKeyId;

    /** 当前启用 JWT 密钥版本；未配置时允许为空。 */
    private String jwtKeyVersion;

    /** JWT 签名算法标识，不包含密钥正文。 */
    private String jwtAlgorithm;

    /** JWT 最大有效期，单位秒；未配置时允许为空。 */
    private Long jwtExpiresSeconds;

    /** JWT 密钥生效时间，精度为毫秒。 */
    private LocalDateTime jwtEffectiveTime;

    /** JWT 密钥记录最后更新时间，参与 revision 计算。 */
    private LocalDateTime jwtModifiedTime;

    /** 当前启用平台请求体密钥记录主键；未配置时允许为空。 */
    private Long platformKeyId;

    /** 平台请求体密钥算法标识，不包含公私钥正文。 */
    private String platformAlgorithm;

    /** 平台请求体 RSA 密钥位数；未配置时允许为空。 */
    private Integer platformKeySize;

    /** 平台请求体密钥记录最后更新时间，参与 revision 计算。 */
    private LocalDateTime platformModifiedTime;

    /** 当前启用商户响应公钥记录主键；未配置时允许为空。 */
    private Long responseKeyId;

    /** 商户响应加密算法标识，不包含公钥正文。 */
    private String responseAlgorithm;

    /** 商户响应 RSA 密钥位数；未配置时允许为空。 */
    private Integer responseKeySize;

    /** 商户响应公钥记录最后更新时间，参与 revision 计算。 */
    private LocalDateTime responseModifiedTime;

    /** 三类当前密钥元数据的 SHA-256 组合版本，用于本地缓存版本隔离。 */
    private String revision;
}
