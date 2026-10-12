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
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.BatchOptions;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinRangeIndex
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 支付与 OpenAPI 共用的 Card BIN 三位分片索引；查询按 generation 隔离，发布只从 SLAVE 构建数据库有界永久缓存
 * @status : update
 */
@Slf4j
@Service
public class CardBinRangeIndex {

    private static final String CACHE_NAMESPACE = "card-bin-range";
    private static final String GENERATION_FIELD = "_generation";
    private static final int SHARD_PREFIX_LENGTH = 3;
    private static final int SHARD_COUNT = 1_000;
    private static final int NORMALIZED_BIN_LENGTH = 11;
    private static final long SHARD_DIVISOR = 100_000_000L;
    private static final long MAX_BIN_VALUE = 99_999_999_999L;
    private static final int ESTIMATED_HASH_ENTRY_OVERHEAD_BYTES = 64;
    private static final String LEGACY_SLOT_IDENTITY = "card-bin-range-index";
    private static final String LEGACY_SLOT_A = "a";
    private static final String LEGACY_SLOT_B = "b";

    private final StringRedisTemplate redisTemplate;
    private final Optional<RedissonClient> redissonClient;
    private final PaymentRedisProperties redisProperties;
    private final CardBinLookupMapper mapper;
    private final RedisCacheGenerationStore generationStore;
    private final int pageSize;
    private final int maxCandidatesPerPrefix;
    private final long maxIndexedEntries;
    private final int writeBatchEntries;
    private final int writeBatchBytes;
    private final long maxSnapshotBytes;
    private final Duration publicationLease;
    private final AtomicLong lastWarningMillis = new AtomicLong();

    /**
     * 创建 Card BIN 分片索引。
     *
     * @param redisTemplate Redis 字符串模板
     * @param redissonClient 可选 Redisson 客户端；存在时并行清理和激活全部分片
     * @param redisProperties 环境隔离 Key 配置
     * @param mapper Card BIN 从库 Mapper
     * @param generationStore 管理端写事务与查询端共用的缓存代际门禁
     * @param pageSize 单次数据库分页行数
     * @param maxCandidatesPerPrefix 单个前缀字段允许保存的候选上限
     * @param maxIndexedEntries 单次发布允许写入的最大分片索引条数
     * @param writeBatchEntries 单次 Lua 最多追加的候选条数
     * @param writeBatchBytes 单次 Lua 参数的估算字节上限
     * @param maxSnapshotBytes 完整索引的估算 Redis 字节预算
     * @param publicationLease 发布锁租约；每个数据库分页完成后续期
     */
    public CardBinRangeIndex(
            StringRedisTemplate redisTemplate,
            Optional<RedissonClient> redissonClient,
            PaymentRedisProperties redisProperties,
            CardBinLookupMapper mapper,
            RedisCacheGenerationStore generationStore,
            @Value("${payment.card-bin.cache.index-page-size:2000}") int pageSize,
            @Value("${payment.card-bin.cache.max-candidates-per-prefix:64}") int maxCandidatesPerPrefix,
            @Value("${payment.card-bin.cache.max-expanded-entries:3000000}") long maxIndexedEntries,
            @Value("${payment.card-bin.cache.write-batch-entries:100}") int writeBatchEntries,
            @Value("${payment.card-bin.cache.write-batch-bytes:131072}") int writeBatchBytes,
            @Value("${payment.card-bin.cache.max-snapshot-bytes:536870912}") long maxSnapshotBytes,
            @Value("${payment.card-bin.cache.publication-lease:PT5M}") Duration publicationLease) {
        if (pageSize <= 0 || pageSize > 10_000
                || maxCandidatesPerPrefix <= 0 || maxCandidatesPerPrefix > 1_000
                || maxIndexedEntries <= 0
                || writeBatchEntries <= 0 || writeBatchEntries > 200
                || writeBatchBytes < 1024 || writeBatchBytes > 1024 * 1024
                || maxSnapshotBytes < writeBatchBytes
                || publicationLease == null || publicationLease.isZero() || publicationLease.isNegative()) {
            throw new IllegalArgumentException("Card BIN range index limits are invalid");
        }
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.redisProperties = redisProperties;
        this.mapper = mapper;
        this.generationStore = generationStore;
        this.pageSize = pageSize;
        this.maxCandidatesPerPrefix = maxCandidatesPerPrefix;
        this.maxIndexedEntries = maxIndexedEntries;
        this.writeBatchEntries = writeBatchEntries;
        this.writeBatchBytes = writeBatchBytes;
        this.maxSnapshotBytes = maxSnapshotBytes;
        this.publicationLease = publicationLease;
    }

    /**
     * 从当前完整代际查询十一位 BIN。
     *
     * <p>先读取管理端写事务建立的 generation 门禁，再对输入前三位对应的唯一 Hash 执行一次
     * HMGET。Hash 的 generation 必须与当前可读代际一致；发布中、分片不完整、载荷损坏或
     * Redis 故障均返回 unavailable，由调用方执行一次 SLAVE SQL。完整分片中没有候选属于
     * 权威未命中，不为商户输入创建负缓存 Key。</p>
     *
     * @param cardBinPrefix 规范化后的十一位数字
     * @return 可用索引中的命中或未命中，或者不可用状态
     */
    public CardBinRangeIndexLookup lookup(String cardBinPrefix) {
        if (cardBinPrefix == null || !cardBinPrefix.matches("^[0-9]{11}$")) {
            throw new IllegalArgumentException("cardBinPrefix must be 11 digits");
        }
        try {
            RedisCacheGenerationState state = generationStore.current(CACHE_NAMESPACE);
            if (!state.cacheReadable() || !StringUtils.hasText(state.generation())) {
                return CardBinRangeIndexLookup.unavailable();
            }
            List<Object> values = redisTemplate.opsForHash().multiGet(
                    shardKey(cardBinPrefix.substring(0, SHARD_PREFIX_LENGTH)),
                    lookupFields(cardBinPrefix)
            );
            if (values == null || values.size() != NORMALIZED_BIN_LENGTH - SHARD_PREFIX_LENGTH + 2
                    || !Objects.equals(state.generation(), values.get(0))) {
                return CardBinRangeIndexLookup.unavailable();
            }
            List<CardBinRangeIndexEntry> candidates = decodeCandidates(values);
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
     * 判断 Redis 中是否存在与当前可读 generation 完全一致的已发布索引。
     *
     * @return true 表示支付服务启动时无需重复扫描从库
     */
    public boolean hasActiveSnapshot() {
        try {
            RedisCacheGenerationState state = generationStore.current(CACHE_NAMESPACE);
            return state.cacheReadable()
                    && StringUtils.hasText(state.generation())
                    && Objects.equals(state.generation(),
                    redisTemplate.opsForValue().get(readyGenerationKey()));
        } catch (RuntimeException exception) {
            warn("ACTIVE_CHECK", exception);
            return false;
        }
    }

    /**
     * 从 SLAVE 分页重建指定 generation 的永久分片索引。
     *
     * <p>调用方必须已经通过 {@link RedisCacheGenerationStore#begin(String, Duration)} 冻结该命名空间。
     * 发布器先删除固定的 000 至 999 分片，再按数据库区间最长公共前缀写入。普通区间只写一个
     * 分片；跨越三位边界的宽区间只复制到实际覆盖的分片。源签名前后一致后，才给全部分片写入
     * generation 并设置完成标记；外层提交 generation 前，支付和 OpenAPI 查询始终回源 SLAVE。
     * 分片数据不设置 TTL，后续变更由管理端可靠 MQ 在从库同步完成后触发整批重建。</p>
     *
     * @param generation 本次管理端发布凭证中的目标代际
     * @return 完整写入的目标代际
     */
    @DS(DataSourceName.SLAVE)
    public String publishFromReplica(String generation) {
        requireGeneration(generation);
        String ownerToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                publicationLockKey(), ownerToken, publicationLease);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("Card BIN index publication is already in progress");
        }

        long indexedEntries = 0L;
        long snapshotBytes = 0L;
        long sourceRows = 0L;
        long lastId = 0L;
        List<IndexWrite> writes = new ArrayList<>(writeBatchEntries);
        long batchBytes = 0L;
        try {
            redisTemplate.delete(readyGenerationKey());
            // 发布门禁已使新查询回源；同时移除旧 A/B 指针，避免滚动升级中的旧实例继续读取旧快照。
            clearLegacySnapshot();
            clearAllShards();
            CardBinIndexSourceSignatureDO before = requireSignature(mapper.selectIndexSourceSignature());
            while (true) {
                List<CardBinRangeDO> page = mapper.selectIndexPage(lastId, pageSize);
                if (page == null || page.isEmpty()) {
                    break;
                }
                for (CardBinRangeDO row : page) {
                    validateRow(row, lastId);
                    String payload = JsonUtils.toJsonString(toEntry(row));
                    int payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
                    for (IndexCoordinate coordinate : indexCoordinates(
                            row.getCardBinStart(), row.getCardBinEnd())) {
                        long entryBytes = coordinate.field().length() + payloadBytes
                                + ESTIMATED_HASH_ENTRY_OVERHEAD_BYTES;
                        if (++indexedEntries > maxIndexedEntries) {
                            throw new IllegalStateException("Card BIN index entry limit exceeded");
                        }
                        if (entryBytes > writeBatchBytes) {
                            throw new IllegalStateException(
                                    "Card BIN index entry exceeds write batch byte limit");
                        }
                        if (snapshotBytes + entryBytes > maxSnapshotBytes) {
                            throw new IllegalStateException("Card BIN snapshot byte budget exceeded");
                        }
                        if (!writes.isEmpty() && (writes.size() >= writeBatchEntries
                                || batchBytes + entryBytes > writeBatchBytes)) {
                            appendBatch(writes);
                            writes.clear();
                            batchBytes = 0L;
                        }
                        writes.add(new IndexWrite(coordinate.shard(), coordinate.field(), payload));
                        batchBytes += entryBytes;
                        snapshotBytes += entryBytes;
                    }
                    lastId = row.getId();
                    sourceRows++;
                }
                renewPublicationLease(ownerToken);
                if (page.size() < pageSize) {
                    break;
                }
            }
            appendBatch(writes);
            CardBinIndexSourceSignatureDO after = requireSignature(mapper.selectIndexSourceSignature());
            if (sourceRows != before.getRowCount()
                    || !Objects.equals(before.getRowCount(), after.getRowCount())
                    || !Objects.equals(before.getChecksum(), after.getChecksum())) {
                throw new IllegalStateException("Card BIN replica changed while building index");
            }
            renewPublicationLease(ownerToken);
            markAllShardsComplete(generation);
            redisTemplate.opsForValue().set(readyGenerationKey(), generation);
            log.info("event: CARD_BIN_RANGE_INDEX_PUBLISHED generation: {} sourceRows: {} indexedEntries: {} snapshotBytes: {} shards: {}",
                    generation, sourceRows, indexedEntries, snapshotBytes, SHARD_COUNT);
        } catch (RuntimeException exception) {
            releasePublicationLock(ownerToken, exception);
            throw exception;
        }
        releasePublicationLock(ownerToken, null);
        return generation;
    }

    /**
     * 删除旧版固定 A/B 快照 Key。
     *
     * <p>四个 Key 使用同一固定 Hash Tag，可在 Redis Cluster 中一次 UNLINK。这里只处理确定性旧 Key，
     * 不执行 SCAN，也不根据商户输入构造删除范围。任一 Redis 异常都会让本次发布失败并保持数据库回源。</p>
     */
    private void clearLegacySnapshot() {
        redisTemplate.unlink(List.of(
                legacySnapshotKey("active"),
                legacySnapshotKey("slot", LEGACY_SLOT_A),
                legacySnapshotKey("slot", LEGACY_SLOT_B),
                legacySnapshotKey("publication-lock")
        ));
    }

    /**
     * 把一批候选按三位分片分组；每个 Lua 只操作一个 Redis Cluster Key，避免跨槽和超大参数。
     */
    private void appendBatch(List<IndexWrite> writes) {
        if (writes.isEmpty()) {
            return;
        }
        Map<String, List<IndexWrite>> byShard = new LinkedHashMap<>();
        for (IndexWrite write : writes) {
            byShard.computeIfAbsent(write.shard(), ignored -> new ArrayList<>()).add(write);
        }
        for (Map.Entry<String, List<IndexWrite>> shardBatch : byShard.entrySet()) {
            List<String> arguments = new ArrayList<>(shardBatch.getValue().size() * 2 + 1);
            arguments.add(String.valueOf(maxCandidatesPerPrefix));
            for (IndexWrite write : shardBatch.getValue()) {
                arguments.add(write.field());
                arguments.add(write.payload());
            }
            Long appended = redisTemplate.execute(
                    PaymentRedisScripts.cardBinBucketAppendV1(),
                    List.of(shardKey(shardBatch.getKey())),
                    arguments.toArray()
            );
            if (!Objects.equals(appended, (long) shardBatch.getValue().size())) {
                throw new IllegalStateException(
                        "Card BIN shard append returned an invalid count");
            }
        }
    }

    /** 删除固定数量的数据库派生分片；Redisson 可用时使用跨节点批处理降低发布耗时。 */
    private void clearAllShards() {
        if (redissonClient.isPresent()) {
            RBatch batch = redissonClient.get().createBatch(BatchOptions.defaults()
                    .executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                    .skipResult());
            for (int shard = 0; shard < SHARD_COUNT; shard++) {
                batch.getMap(shardKey(shardName(shard)), StringCodec.INSTANCE).unlinkAsync();
            }
            batch.execute();
            return;
        }
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            redisTemplate.unlink(shardKey(shardName(shard)));
        }
    }

    /** 全部数据和源签名校验完成后才标记分片代际；空分片也会成为权威未命中来源。 */
    private void markAllShardsComplete(String generation) {
        if (redissonClient.isPresent()) {
            RBatch batch = redissonClient.get().createBatch(BatchOptions.defaults()
                    .executionMode(BatchOptions.ExecutionMode.IN_MEMORY)
                    .skipResult());
            for (int shard = 0; shard < SHARD_COUNT; shard++) {
                batch.<String, String>getMap(shardKey(shardName(shard)), StringCodec.INSTANCE)
                        .fastPutAsync(GENERATION_FIELD, generation);
            }
            batch.execute();
            return;
        }
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            redisTemplate.opsForHash().put(
                    shardKey(shardName(shard)), GENERATION_FIELD, generation);
        }
    }

    private List<CardBinRangeIndexEntry> decodeCandidates(List<Object> values) {
        int lookupCandidateLimit = Math.multiplyExact(
                maxCandidatesPerPrefix,
                NORMALIZED_BIN_LENGTH - SHARD_PREFIX_LENGTH + 1);
        List<CardBinRangeIndexEntry> candidates = new ArrayList<>();
        for (int index = 1; index < values.size(); index++) {
            Object value = values.get(index);
            if (value == null || !StringUtils.hasText(value.toString())) {
                continue;
            }
            candidates.addAll(JsonUtils.parseArray(
                    value.toString(), CardBinRangeIndexEntry.class));
            if (candidates.size() > lookupCandidateLimit) {
                throw new IllegalStateException(
                        "Card BIN prefix lookup exceeds configured candidate limit");
            }
        }
        return candidates;
    }

    private List<Object> lookupFields(String cardBinPrefix) {
        List<Object> fields = new ArrayList<>(
                NORMALIZED_BIN_LENGTH - SHARD_PREFIX_LENGTH + 2);
        fields.add(GENERATION_FIELD);
        for (int length = SHARD_PREFIX_LENGTH; length <= NORMALIZED_BIN_LENGTH; length++) {
            fields.add(length + ":" + cardBinPrefix.substring(0, length));
        }
        return fields;
    }

    /**
     * 将一个数值区间映射到固定三位分片及其最窄公共前缀字段。
     * 跨分片宽区间在每个实际覆盖分片保存一次，并由查询时的精确区间判断排除假阳性。
     */
    static List<IndexCoordinate> indexCoordinates(long cardBinStart, long cardBinEnd) {
        String start = elevenDigitValue(cardBinStart);
        String end = elevenDigitValue(cardBinEnd);
        int commonLength = 0;
        while (commonLength < NORMALIZED_BIN_LENGTH
                && start.charAt(commonLength) == end.charAt(commonLength)) {
            commonLength++;
        }
        int firstShard = Math.toIntExact(cardBinStart / SHARD_DIVISOR);
        int lastShard = Math.toIntExact(cardBinEnd / SHARD_DIVISOR);
        List<IndexCoordinate> coordinates = new ArrayList<>(lastShard - firstShard + 1);
        for (int shard = firstShard; shard <= lastShard; shard++) {
            String shardName = shardName(shard);
            int fieldLength = Math.max(commonLength, SHARD_PREFIX_LENGTH);
            String fieldPrefix = commonLength >= SHARD_PREFIX_LENGTH
                    ? start.substring(0, commonLength) : shardName;
            coordinates.add(new IndexCoordinate(
                    shardName, fieldLength + ":" + fieldPrefix));
        }
        return coordinates;
    }

    private void renewPublicationLease(String ownerToken) {
        Long renewed = redisTemplate.execute(
                PaymentRedisScripts.tokenLeaseRenewV1(),
                List.of(publicationLockKey()),
                ownerToken,
                String.valueOf(publicationLease.toMillis())
        );
        if (!Objects.equals(renewed, 1L)) {
            throw new IllegalStateException("Card BIN index publication ownership was lost");
        }
    }

    private void releasePublicationLock(String ownerToken, RuntimeException original) {
        try {
            Long released = redisTemplate.execute(
                    PaymentRedisScripts.tokenLeaseReleaseV1(),
                    List.of(publicationLockKey()),
                    ownerToken
            );
            if (!Objects.equals(released, 1L)) {
                throw new IllegalStateException(
                        "Card BIN index publication lock was not released by its owner");
            }
        } catch (RuntimeException releaseException) {
            if (original != null) {
                original.addSuppressed(releaseException);
                return;
            }
            throw releaseException;
        }
    }

    private static String elevenDigitValue(long value) {
        if (value < 0 || value > MAX_BIN_VALUE) {
            throw new IllegalArgumentException(
                    "Card BIN index value must contain at most 11 digits");
        }
        String digits = Long.toString(value);
        return "0".repeat(NORMALIZED_BIN_LENGTH - digits.length()) + digits;
    }

    private static String shardName(int shard) {
        if (shard < 0 || shard >= SHARD_COUNT) {
            throw new IllegalArgumentException("Card BIN shard must be between 000 and 999");
        }
        return String.format("%03d", shard);
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
                || row.getBinLength() == null || row.getBinLength() < 6
                || row.getBinLength() > NORMALIZED_BIN_LENGTH) {
            throw new IllegalStateException("Card BIN replica contains an invalid index row");
        }
    }

    private CardBinIndexSourceSignatureDO requireSignature(
            CardBinIndexSourceSignatureDO signature) {
        if (signature == null || signature.getRowCount() == null || signature.getRowCount() < 0
                || signature.getChecksum() == null) {
            throw new IllegalStateException("Card BIN replica signature is unavailable");
        }
        return signature;
    }

    private void requireGeneration(String generation) {
        if (!StringUtils.hasText(generation) || generation.length() > 128
                || !generation.matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("Card BIN generation is invalid");
        }
    }

    private String shardKey(String shard) {
        return redisProperties.businessKey("cardBin", shard);
    }

    private String readyGenerationKey() {
        return redisProperties.businessKey("cardBin", "index", "ready");
    }

    private String publicationLockKey() {
        return redisProperties.businessKey("cardBin", "index", "publication-lock");
    }

    private String legacySnapshotKey(String... businessSegments) {
        return redisProperties.coLocatedBusinessKey(
                "cardBin", "rangeIndex", LEGACY_SLOT_IDENTITY,
                businessSegments);
    }

    private void warn(String operation, RuntimeException exception) {
        long now = System.currentTimeMillis();
        long previous = lastWarningMillis.get();
        if (now - previous >= 60_000 && lastWarningMillis.compareAndSet(previous, now)) {
            log.warn("event: CARD_BIN_RANGE_INDEX_FALLBACK operation: {} exceptionType: {}",
                    operation, exception.getClass().getSimpleName(), exception);
        }
    }

    /** Redis 三位分片及该分片内的前缀字段。 */
    record IndexCoordinate(String shard, String field) {
    }

    /** 单条待写入的数据库派生候选，不包含商户查询输入。 */
    private record IndexWrite(String shard, String field, String payload) {
    }
}
