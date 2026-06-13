import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

record Merchant(String merchantId, String merchantName) {}

String url = "jdbc:mysql://127.0.0.1:3306/payment_acquiring?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
String username = "root";
String password = "scott123456";
Path auditFile = Path.of("audit/merchant-openapi-key-reset-20260613.json");
LocalDateTime now = LocalDateTime.now();
Timestamp nowTs = Timestamp.valueOf(now);
Timestamp expireTs = Timestamp.valueOf(now.plusYears(10));
String versionSuffix = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
SecureRandom secureRandom = new SecureRandom();

String jsonEscape(String value) {
    if (value == null) {
        return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
}

String jsonField(String name, Object value, boolean last) {
    String raw = value == null ? "" : value.toString();
    return "    \"" + name + "\": \"" + jsonEscape(raw) + "\"" + (last ? "" : ",") + "\n";
}

String base64UrlSecret(int bytes) {
    byte[] secret = new byte[bytes];
    secureRandom.nextBytes(secret);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
}

KeyPair rsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048, secureRandom);
    return generator.generateKeyPair();
}

String x509Public(KeyPair keyPair) {
    return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
}

String pkcs8Private(KeyPair keyPair) {
    return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
}

String fingerprint(String value) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    String encoded = Base64.getEncoder().encodeToString(digest);
    return encoded.substring(0, 24);
}

Long selectExistingKeyRow(Connection connection, String table, String merchantId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
            "select id from " + table + " where merchant_id = ? and deleted = 0 order by id desc limit 1")) {
        statement.setString(1, merchantId);
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getLong("id");
            }
            return null;
        }
    }
}

void upsertPlatformKey(Connection connection, String merchantId, String publicKey, String privateKey) throws SQLException {
    Long rowId = selectExistingKeyRow(connection, "base_platform_payload_key", merchantId);
    if (rowId == null) {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into base_platform_payload_key
                (merchant_id, public_key_x509_base64, private_key_pkcs8_base64, algorithm, key_size, enabled, gmt_create, gmt_modified, deleted)
                values (?, ?, ?, 'RSA-OAEP-256', 2048, 1, ?, ?, 0)
                """)) {
            statement.setString(1, merchantId);
            statement.setString(2, publicKey);
            statement.setString(3, privateKey);
            statement.setTimestamp(4, nowTs);
            statement.setTimestamp(5, nowTs);
            statement.executeUpdate();
        }
        return;
    }
    try (PreparedStatement statement = connection.prepareStatement("""
            update base_platform_payload_key
            set public_key_x509_base64 = ?,
                private_key_pkcs8_base64 = ?,
                algorithm = 'RSA-OAEP-256',
                key_size = 2048,
                enabled = 1,
                gmt_modified = ?
            where id = ?
            """)) {
        statement.setString(1, publicKey);
        statement.setString(2, privateKey);
        statement.setTimestamp(3, nowTs);
        statement.setLong(4, rowId);
        statement.executeUpdate();
    }
}

void upsertResponseKey(Connection connection, String merchantId, String publicKey, String privateKey) throws SQLException {
    Long rowId = selectExistingKeyRow(connection, "base_merchant_response_key", merchantId);
    if (rowId == null) {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into base_merchant_response_key
                (merchant_id, public_key_x509_base64, private_key_pkcs8_base64, algorithm, key_size, enabled, gmt_create, gmt_modified, deleted)
                values (?, ?, ?, 'RSA-OAEP-256', 2048, 1, ?, ?, 0)
                """)) {
            statement.setString(1, merchantId);
            statement.setString(2, publicKey);
            statement.setString(3, privateKey);
            statement.setTimestamp(4, nowTs);
            statement.setTimestamp(5, nowTs);
            statement.executeUpdate();
        }
        return;
    }
    try (PreparedStatement statement = connection.prepareStatement("""
            update base_merchant_response_key
            set public_key_x509_base64 = ?,
                private_key_pkcs8_base64 = ?,
                algorithm = 'RSA-OAEP-256',
                key_size = 2048,
                enabled = 1,
                gmt_modified = ?
            where id = ?
            """)) {
        statement.setString(1, publicKey);
        statement.setString(2, privateKey);
        statement.setTimestamp(3, nowTs);
        statement.setLong(4, rowId);
        statement.executeUpdate();
    }
}

void insertJwtKey(Connection connection, String merchantId, String merchantKey) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
            update base_merchant_jwt_key
            set enabled = 0, expire_time = ?, gmt_modified = ?
            where merchant_id = ? and deleted = 0 and enabled = 1
            """)) {
        statement.setTimestamp(1, nowTs);
        statement.setTimestamp(2, nowTs);
        statement.setString(3, merchantId);
        statement.executeUpdate();
    }
    try (PreparedStatement statement = connection.prepareStatement("""
            insert into base_merchant_jwt_key
            (merchant_id, key_version, merchant_key, algorithm, expires_seconds, enabled, effective_time, expire_time, gmt_create, gmt_modified, deleted)
            values (?, ?, ?, 'HS256', 180, 1, ?, ?, ?, ?, 0)
            """)) {
        statement.setString(1, merchantId);
        statement.setString(2, "jwt-" + versionSuffix);
        statement.setString(3, merchantKey);
        statement.setTimestamp(4, nowTs);
        statement.setTimestamp(5, expireTs);
        statement.setTimestamp(6, nowTs);
        statement.setTimestamp(7, nowTs);
        statement.executeUpdate();
    }
}

List<Merchant> merchants = new ArrayList<>();
try (Connection connection = DriverManager.getConnection(url, username, password);
     PreparedStatement statement = connection.prepareStatement("""
             select merchant_id, merchant_name
             from base_merchant_info
             where deleted = 0
             order by merchant_id
             """);
     ResultSet resultSet = statement.executeQuery()) {
    while (resultSet.next()) {
        merchants.add(new Merchant(resultSet.getString("merchant_id"), resultSet.getString("merchant_name")));
    }
}

if (merchants.isEmpty()) {
    throw new IllegalStateException("No merchants found in base_merchant_info");
}

StringBuilder json = new StringBuilder();
json.append("[\n");
try (Connection connection = DriverManager.getConnection(url, username, password)) {
    connection.setAutoCommit(false);
    try {
        int index = 0;
        for (Merchant merchant : merchants) {
            String merchantKey = base64UrlSecret(32);
            KeyPair platformPair = rsaKeyPair();
            KeyPair responsePair = rsaKeyPair();
            String platformPublic = x509Public(platformPair);
            String platformPrivate = pkcs8Private(platformPair);
            String responsePublic = x509Public(responsePair);
            String responsePrivate = pkcs8Private(responsePair);

            insertJwtKey(connection, merchant.merchantId(), merchantKey);
            upsertPlatformKey(connection, merchant.merchantId(), platformPublic, platformPrivate);
            upsertResponseKey(connection, merchant.merchantId(), responsePublic, responsePrivate);

            if (index > 0) {
                json.append(",\n");
            }
            json.append("  {\n");
            json.append(jsonField("merchantId", merchant.merchantId(), false));
            json.append(jsonField("merchantName", merchant.merchantName(), false));
            json.append(jsonField("jwtAlgorithm", "HS256", false));
            json.append(jsonField("jwtExpiresSeconds", 180, false));
            json.append(jsonField("merchantKey", merchantKey, false));
            json.append(jsonField("merchantKeyFingerprint", fingerprint(merchantKey), false));
            json.append(jsonField("requestCryptoAlgorithm", "RSA-OAEP-256 + AES-256-GCM", false));
            json.append(jsonField("platformPublicKeyX509Base64", platformPublic, false));
            json.append(jsonField("platformPrivateKeyPkcs8Base64", platformPrivate, false));
            json.append(jsonField("platformPublicKeyFingerprint", fingerprint(platformPublic), false));
            json.append(jsonField("responseCryptoAlgorithm", "RSA-OAEP-256 + AES-256-GCM", false));
            json.append(jsonField("merchantResponsePublicKeyX509Base64", responsePublic, false));
            json.append(jsonField("merchantResponsePrivateKeyPkcs8Base64", responsePrivate, false));
            json.append(jsonField("merchantResponsePublicKeyFingerprint", fingerprint(responsePublic), true));
            json.append("  }");
            index++;
        }
        connection.commit();
    } catch (Exception exception) {
        connection.rollback();
        throw exception;
    }
}
json.append("\n]\n");
Files.createDirectories(auditFile.getParent());
Files.writeString(auditFile, json.toString(), StandardCharsets.UTF_8);
System.out.println("Reset merchant OpenAPI keys: " + merchants.size());
System.out.println("Audit file: " + auditFile.toAbsolutePath());

try (Connection connection = DriverManager.getConnection(url, username, password);
     Statement statement = connection.createStatement()) {
    try (ResultSet resultSet = statement.executeQuery("""
            select
              (select count(*) from base_merchant_info where deleted = 0) merchant_count,
              (select count(*) from base_merchant_jwt_key where deleted = 0 and enabled = 1) active_jwt_count,
              (select count(*) from base_platform_payload_key where deleted = 0 and enabled = 1 and public_key_x509_base64 is not null and private_key_pkcs8_base64 is not null) platform_key_count,
              (select count(*) from base_merchant_response_key where deleted = 0 and enabled = 1 and public_key_x509_base64 is not null and private_key_pkcs8_base64 is not null) response_key_count
            """)) {
        if (resultSet.next()) {
            System.out.println("merchant_count=" + resultSet.getInt("merchant_count"));
            System.out.println("active_jwt_count=" + resultSet.getInt("active_jwt_count"));
            System.out.println("platform_key_count=" + resultSet.getInt("platform_key_count"));
            System.out.println("response_key_count=" + resultSet.getInt("response_key_count"));
        }
    }
}
