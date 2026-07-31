package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityCacheInvalidationOutboxDO
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 管理端数据层永久缓存失效 Outbox 持久化模型，保存精确缓存目标、门禁租约和重试状态
 * @status : create
 *
 * <p>类名对应兼容保留的数据库表名；字段不包含商户专属语义，可保存商户号或平台配置键。</p>
 */
@Data
@TableName("merchant_security_cache_invalidation_outbox")
public class MerchantSecurityCacheInvalidationOutboxDO {

    /** Outbox 自增主键，不作为跨服务幂等标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 缓存失效事件唯一标识，用于发布重试和幂等查询。 */
    private String eventId;

    /** 需要失效的 Spring Cache 名称。 */
    private String cacheName;

    /** 缓存业务键，允许为商户号或已登记的平台公开配置键，不允许为空。 */
    private String businessKey;

    /** 失效门闩租约的不透明令牌，属于内部控制数据，不得写入普通日志。 */
    private String gateToken;

    /** 发布状态，取值为 INIT、FAILED 或不可逆的 SENT。 */
    private String eventStatus;

    /** 发布失败累计次数，用于补偿监控。 */
    private Integer retryCount;

    /** 下一次允许补偿发布的时间；首次发布时允许为空。 */
    private LocalDateTime nextRetryTime;

    /** 首次成功完成缓存清理并释放门闩的时间。 */
    private LocalDateTime publishedTime;

    /** 最近一次失败原因，最长 512 个字符，不得包含密钥或商户敏感资料。 */
    private String failureReason;

    /** Outbox 状态更新使用的乐观锁版本。 */
    private Integer version;

    /** 事件持久化时间，精度为毫秒。 */
    private LocalDateTime createTime;

    /** 最近一次状态更新或重试时间，精度为毫秒。 */
    private LocalDateTime updateTime;
}
