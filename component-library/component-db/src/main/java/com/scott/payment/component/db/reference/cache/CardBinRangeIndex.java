package com.scott.payment.component.db.reference.cache;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.reference.entity.CardBinIndexSourceSignatureDO;
import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.mapper.CardBinLookupMapper;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.CardBinRangeIndexEntry;
import com.scott.payment.component.db.reference.model.CardBinRangeIndexLookup;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinRangeIndex
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 支付与 OpenAPI 共用的 Card BIN A/B 快照索引；读路径单次 Lua 定位六位桶，发布路径只从 SLAVE 构建数据库有界数据
 * @status : create
 */
@Slf4j
@Service
public class CardBinRangeIndex {

    private static final String SLOT_A = "a";
    private static final String SLOT_B = "b";
    private static final String SLOT_IDENTITY = "card-bin-range-index";
    private static final long MAX_BIN_VALUE = 99_999_999_999L;
    private static final int ESTIMATED_HASH_ENTRY_OVERHEAD_BYTES = 64;

    private final StringRedisTemplate redisTemplate;
    private final PaymentRedisProperties redisProperties;
    private final CardBinLookupMapper mapper;
    private final int pageSize;
    private final int maxCandidatesPerBucket;
    private final long maxExpandedEntries;
    private final int writeBatchEntries;
    private final int writeBatchBytes;
    private final long maxSnapshotBytes;
    private final Duration publicationLease;
    private final AtomicLong lastWarningMillis = new AtomicLong();

    /**
     * 创建 Card BIN 快照索引。
     *
     * @param redisTemplate Redis 字符串模板
     * @param redisProperties 环境隔离 Key 配置
     * @param mapper Card BIN 从库 Mapper
     * @param pageSize 单次数据库分页行数
     * @param maxCandidatesPerBucket 单个六位桶候选上限
     * @param maxExpandedEntries 一次发布允许写入的最大展开记录数
     * @param writeBatchEntries 单次 Lua 最多写入的桶候选数量
     * @param writeBatchBytes 单次 Lua 参数的估算字节上限
     * @param maxSnapshotBytes 单个完整快照的估算 Redis 字节预算
     * @param publicationLease 发布令牌租约；每个成功写批次都会续期
     */
    public CardBinRangeIndex(
            StringRedisTemplate redisTemplate,
            PaymentRedisProperties redisProperties,
            CardBinLookupMapper mapper,
            @Value("${payment.card-bin.cache.index-page-size:2000}") int pageSize,
            @Value("${payment.card-bin.cache.max-candidates-per-bucket:256}") int maxCandidatesPerBucket,
            @Value("${payment.card-bin.cache.max-expanded-entries:3000000}") long maxExpandedEntries,
            @Value("${payment.card-bin.cache.write-batch-entries:500}") int writeBatchEntries,
            @Value("${payment.card-bin.cache.write-batch-bytes:524288}") int writeBatchBytes,
            @Value("${payment.card-bin.cache.max-snapshot-bytes:536870912}") long maxSnapshotBytes,
            @Value("${payment.card-bin.cache.publication-lease:PT5M}") Duration publicationLease) {
        if (pageSize <= 0 || pageSize > 10_000
                || maxCandidatesPerBucket <= 0 || maxCandidatesPerBucket > 10_000
                || maxExpandedEntries <= 0
                || writeBatchEntries <= 0 || writeBatchEntries > 10_000
                || writeBatchBytes < 1024 || writeBatchBytes > 16 * 1024 * 1024
                || maxSnapshotBytes < writeBatchBytes
                || publicationLease == null || publicationLease.isZero() || publicationLease.isNegative()) {
            throw new IllegalArgumentException("Card BIN range index limits are invalid");
        }
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.mapper = mapper;
        this.pageSize = pageSize;
        this.maxCandidatesPerBucket = maxCandidatesPerBucket;
        this.maxExpandedEntries = maxExpandedEntries;
        this.writeBatchEntries = writeBatchEntries;
        this.writeBatchBytes = writeBatchBytes;
        this.maxSnapshotBytes = maxSnapshotBytes;
        this.publicationLease = publicationLease;
    }

    /**
     * 从当前活动快照查询十一位 BIN。
     *
     * <p>Lua 在一次 Redis 往返中读取活动槽和六位桶。桶不存在是完整快照中的权威未命中；
     * Redis 故障、活动槽未初始化或载荷损坏时返回 unavailable，由调用方执行一次 SLAVE SQL。</p>
     *
     * @param cardBinPrefix 规范化后的十一位数字
     * @return 可用快照中的命中/未命中，或不可用状态
     */
    public CardBinRangeIndexLookup lookup(String cardBinPrefix) {
        if (cardBinPrefix == null || !cardBinPrefix.matches("^[0-9]{11}$")) {
            throw new IllegalArgumentException("cardBinPrefix must be 11 digits");
        }
        try {
            String payload = redisTemplate.execute(
                    PaymentRedisScripts.cardBinBucketReadV1(),
                    List.of(activeSlotKey(), slotKey(SLOT_A), slotKey(SLOT_B)),
                    cardBinPrefix.substring(0, 6)
            );
            if (payload == null) {
                return CardBinRangeIndexLookup.unavailable();
            }
            if (payload.isEmpty()) {
                return CardBinRangeIndexLookup.available(CardBinLookupResult.miss(cardBinPrefix));
            }
            List<CardBinRangeIndexEntry> candidates = JsonUtils.parseArray(
                    payload, CardBinRangeIndexEntry.class);
            if (candidates.size() > maxCandidatesPerBucket) {
                throw new IllegalStateException("Card BIN bucket exceeds configured candidate limit");
            }
            long numericValue = Long.parseLong(cardBinPrefix);
            LocalDateTime now = LocalDateTime.now();
            return candidates.stream()
                    .filter(candidate -> candidate.matches(numericValue, now))
                    .max(winnerOrder())
                    .map(candidate -> CardBinRangeIndexLookup.available(
                            toResult(cardBinPrefix, candidate)))
                    .orElseGet(() -> CardBinRangeIndexLookup.available(
                            CardBinLookupResult.miss(cardBinPrefix)));
        } catch (RuntimeException exception) {
            warn("READ", exception);
            return CardBinRangeIndexLookup.unavailable();
        }
    }

    /**
     * 判断是否存在已经完整发布的活动槽，供支付服务启动初始化使用。
     */
    public boolean hasActiveSnapshot() {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(activeSlotKey()));
        } catch (RuntimeException exception) {
            warn("ACTIVE_CHECK", exception);
            return false;
        }
    }

    /**
     * 从 SLAVE 分页构建非活动槽，并在源签名前后一致时原子切换活动指针。
     *
     * <p>发布仅展开数据库记录覆盖的六位桶，Key 数量固定为活动指针、发布锁和两个槽 Hash。
     * 数据库按 ID 分页读取，Lua 参数按条数及字节数双重切分，JVM 不保存完整快照。每批写入都校验
     * 发布令牌并续租；资源上限、Redis 写入或源签名校验失败时保留旧活动槽并异步清理目标槽。
     * 校验成功后，Lua 在同一个原子操作中切换活动指针并 {@code UNLINK} 旧槽，读请求不会观察到空窗。</p>
     *
     * @return 激活的槽位标识
     */
    @DS(DataSourceName.SLAVE)
    public String publishFromReplica() {
        String active = redisTemplate.opsForValue().get(activeSlotKey());
        String target = SLOT_A.equals(active) ? SLOT_B : SLOT_A;
        String targetKey = slotKey(target);
        String ownerToken = UUID.randomUUID().toString();
        Long acquired = redisTemplate.execute(
                PaymentRedisScripts.cardBinSnapshotBeginV1(),
                List.of(publicationLockKey(), targetKey),
                ownerToken,
                String.valueOf(publicationLease.toMillis())
        );
        if (!Objects.equals(acquired, 1L)) {
            throw new IllegalStateException("Card BIN snapshot publication is already in progress");
        }

        // 固定一百万个六位桶计数约占 4 MiB，用有界数组阻止单桶候选异常膨胀。
        int[] candidateCounts = new int[1_000_000];
        long expandedEntries = 0L;
        long snapshotBytes = 0L;
        long sourceRows = 0L;
        long lastId = 0L;
        List<String> arguments = new ArrayList<>(writeBatchEntries * 2);
        long batchBytes = 0L;
        try {
            CardBinIndexSourceSignatureDO before = requireSignature(mapper.selectIndexSourceSignature());
            while (true) {
                List<CardBinRangeDO> page = mapper.selectIndexPage(lastId, pageSize);
                if (page == null || page.isEmpty()) {
                    break;
                }
                for (CardBinRangeDO row : page) {
                    validateRow(row, lastId);
                    CardBinRangeIndexEntry entry = toEntry(row);
                    String payload = JsonUtils.toJsonString(entry);
                    int payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
                    long firstBucket = row.getCardBinStart() / 100_000L;
                    long lastBucket = row.getCardBinEnd() / 100_000L;
                    for (long bucket = firstBucket; bucket <= lastBucket; bucket++) {
                        int bucketIndex = Math.toIntExact(bucket);
                        if (++candidateCounts[bucketIndex] > maxCandidatesPerBucket) {
                            throw new IllegalStateException("Card BIN bucket candidate limit exceeded");
                        }
                        if (++expandedEntries > maxExpandedEntries) {
                            throw new IllegalStateException("Card BIN expanded entry limit exceeded");
                        }
                        String bucketField = String.format("%06d", bucketIndex);
                        long entryBytes = bucketField.length() + payloadBytes
                                + ESTIMATED_HASH_ENTRY_OVERHEAD_BYTES;
                        if (entryBytes > writeBatchBytes) {
                            throw new IllegalStateException("Card BIN index entry exceeds write batch byte limit");
                        }
                        if (snapshotBytes + entryBytes > maxSnapshotBytes) {
                            throw new IllegalStateException("Card BIN snapshot byte budget exceeded");
                        }
                        if (!arguments.isEmpty()
                                && (arguments.size() / 2 >= writeBatchEntries
                                || batchBytes + entryBytes > writeBatchBytes)) {
                            appendBatch(targetKey, ownerToken, arguments);
                            arguments.clear();
                            batchBytes = 0L;
                        }
                        arguments.add(bucketField);
                        arguments.add(payload);
                        batchBytes += entryBytes;
                        snapshotBytes += entryBytes;
                    }
                    lastId = row.getId();
                    sourceRows++;
                }
                if (page.size() < pageSize) {
                    break;
                }
            }
            appendBatch(targetKey, ownerToken, arguments);
            CardBinIndexSourceSignatureDO after = requireSignature(mapper.selectIndexSourceSignature());
            if (sourceRows != before.getRowCount()
                    || !Objects.equals(before.getRowCount(), after.getRowCount())
                    || !Objects.equals(before.getChecksum(), after.getChecksum())) {
                throw new IllegalStateException("Card BIN replica changed while building index");
            }
            Long committed = redisTemplate.execute(
                    PaymentRedisScripts.cardBinSnapshotCommitV1(),
                    List.of(activeSlotKey(), publicationLockKey(), slotKey(opposite(target))),
                    ownerToken,
                    target
            );
            if (!Objects.equals(committed, 1L)) {
                throw new IllegalStateException("Card BIN snapshot publication ownership was lost");
            }
            log.info("event: CARD_BIN_RANGE_INDEX_PUBLISHED slot: {} sourceRows: {} expandedEntries: {} snapshotBytes: {}",
                    target, sourceRows, expandedEntries, snapshotBytes);
            return target;
        } catch (RuntimeException exception) {
            try {
                redisTemplate.execute(
                        PaymentRedisScripts.cardBinSnapshotAbortV1(),
                        List.of(publicationLockKey(), targetKey),
                        ownerToken
                );
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    /**
     * 追加一个严格有界的桶候选批次；Lua 返回 -1 表示发布租约已经被其他发布者取代。
     */
    private void appendBatch(String targetKey, String ownerToken, List<String> fieldValueArguments) {
        if (fieldValueArguments.isEmpty()) {
            return;
        }
        List<String> scriptArguments = new ArrayList<>(fieldValueArguments.size() + 2);
        scriptArguments.add(ownerToken);
        scriptArguments.add(String.valueOf(publicationLease.toMillis()));
        scriptArguments.addAll(fieldValueArguments);
        Long appended = redisTemplate.execute(
                PaymentRedisScripts.cardBinBucketAppendV1(),
                List.of(targetKey, publicationLockKey()),
                scriptArguments.toArray()
        );
        if (!Objects.equals(appended, (long) fieldValueArguments.size() / 2L)) {
            throw new IllegalStateException("Card BIN bucket append lost ownership or returned an invalid count");
        }
    }

    private Comparator<CardBinRangeIndexEntry> winnerOrder() {
        return Comparator.comparingInt(CardBinRangeIndexEntry::binLength)
                .thenComparing(entry -> entry.updateTime() == null
                        ? LocalDateTime.MIN : entry.updateTime())
                .thenComparing(entry -> entry.id() == null ? 0L : entry.id());
    }

    private CardBinLookupResult toResult(String cardBinPrefix, CardBinRangeIndexEntry entry) {
        return new CardBinLookupResult(
                true, cardBinPrefix, entry.binLength(), entry.cardBrand(), entry.cardSubBrand(),
                entry.cardType(), entry.cardLevel(), entry.issuerCountryName(),
                entry.issuerCountryAlpha2(), entry.issuerCountryAlpha3(),
                entry.issuerCountryNumeric(), entry.issuerBank());
    }

    private CardBinRangeIndexEntry toEntry(CardBinRangeDO row) {
        return new CardBinRangeIndexEntry(
                row.getId(), row.getCardBinStart(), row.getCardBinEnd(), row.getBinLength(),
                row.getCardBrand(), row.getCardSubBrand(), row.getCardType(), row.getCardLevel(),
                row.getIssuerCountryName(), row.getIssuerCountryAlpha2(), row.getIssuerCountryAlpha3(),
                row.getIssuerCountryNumeric(), row.getIssuerBank(), row.getEffectiveTime(),
                row.getExpireTime(), row.getUpdateTime());
    }

    private void validateRow(CardBinRangeDO row, long previousId) {
        if (row == null || row.getId() == null || row.getId() <= previousId
                || row.getCardBinStart() == null || row.getCardBinEnd() == null
                || row.getCardBinStart() < 0 || row.getCardBinEnd() > MAX_BIN_VALUE
                || row.getCardBinStart() > row.getCardBinEnd()
                || row.getBinLength() == null || row.getBinLength() < 6 || row.getBinLength() > 11) {
            throw new IllegalStateException("Card BIN replica contains an invalid index row");
        }
    }

    private CardBinIndexSourceSignatureDO requireSignature(CardBinIndexSourceSignatureDO signature) {
        if (signature == null || signature.getRowCount() == null || signature.getRowCount() < 0
                || signature.getChecksum() == null) {
            throw new IllegalStateException("Card BIN replica signature is unavailable");
        }
        return signature;
    }

    private String activeSlotKey() {
        return redisProperties.coLocatedBusinessKey(
                "cardBin", "rangeIndex", SLOT_IDENTITY, "active");
    }

    private String slotKey(String slot) {
        return redisProperties.coLocatedBusinessKey(
                "cardBin", "rangeIndex", SLOT_IDENTITY, "slot", slot);
    }

    private String publicationLockKey() {
        return redisProperties.coLocatedBusinessKey(
                "cardBin", "rangeIndex", SLOT_IDENTITY, "publication-lock");
    }

    /** 返回目标槽的另一侧；提交新快照后该槽已退休，可以异步释放其大 Hash。 */
    private String opposite(String slot) {
        return SLOT_A.equals(slot) ? SLOT_B : SLOT_A;
    }

    private void warn(String operation, RuntimeException exception) {
        long now = System.currentTimeMillis();
        long previous = lastWarningMillis.get();
        if (now - previous >= 60_000 && lastWarningMillis.compareAndSet(previous, now)) {
            log.warn("event: CARD_BIN_RANGE_INDEX_FALLBACK operation: {} exceptionType: {}",
                    operation, exception.getClass().getSimpleName(), exception);
        }
    }
}
