package org.sibyl.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Service;

/** Executes addon-owned SQL exclusively through an addon-scoped MySQL identity. */
@Service
public final class AddonMigrationRunner {
    private static final int MAX_SQL_BYTES=512_000;
    public record Result(int applied,int alreadyApplied) {}

    static String schema(String addonId) {
        if (!AddonDeploymentPlan.validId(addonId))
            throw new IllegalArgumentException("Invalid addon id");
        String suffix=addonId.substring("Sibyl.".length()).replace('-','_');
        if (suffix.length()>48) throw new IllegalArgumentException("Addon id too long for DB schema");
        return "sibyl_ext_"+suffix;
    }

    public Result apply(String addonId, Path archive, List<AddonDeploymentPlan.Operation> operations)
            throws Exception {
        var migrations=operations.stream().filter(o->o.type().equals("migrate-db")).toList();
        if (migrations.isEmpty()) return new Result(0,0);
        String schema=schema(addonId);
        String prefix="SIBYL_EXT_"+addonId.substring("Sibyl.".length())
            .toUpperCase(Locale.ROOT).replace('-','_');
        String url=System.getenv(prefix+"_DB_URL");
        String username=System.getenv(prefix+"_DB_USER");
        String password=System.getenv(prefix+"_DB_PASSWORD");
        if (url==null || username==null || password==null || password.isEmpty()
            || !url.startsWith("jdbc:mysql://")
            || url.contains("allowMultiQueries=true")
            || !username.equals(schema)
            || username.equals(System.getenv("SIBYL_DB_USER")))
            throw new IllegalStateException("Dedicated addon MySQL credentials not provisioned: "+addonId);
        int applied=0,existing=0;
        try (Connection connection=DriverManager.getConnection(url,username,password);
             ZipFile zip=new ZipFile(archive.toFile())) {
            if (!schema.equals(connection.getCatalog()))
                throw new IllegalStateException("Addon database connection has wrong default schema");
            String lockKey="sibyl-ext-migrate-"+schema;
            try (PreparedStatement lock=connection.prepareStatement("SELECT GET_LOCK(?,5)")) {
                lock.setString(1,lockKey);
                try(ResultSet rs=lock.executeQuery()) {
                    if (!rs.next() || rs.getInt(1)!=1)
                        throw new IllegalStateException("Could not obtain addon migration lock");
                }
            }
            try {
                try (Statement st=connection.createStatement()) {
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS sibyl_migrations (
                            filename VARCHAR(180) PRIMARY KEY,
                            sha256 CHAR(64) NOT NULL,
                            state VARCHAR(16) NOT NULL,
                            applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                }
                for (var op:migrations) {
                    ZipEntry entry=zip.getEntry(op.path());
                    if(entry==null || entry.getSize()<1 || entry.getSize()>MAX_SQL_BYTES)
                        throw new IllegalArgumentException("Missing or oversized SQL asset");
                    byte[] bytes;
                    try(InputStream in=zip.getInputStream(entry)) {
                        bytes=in.readNBytes(MAX_SQL_BYTES+1);
                    }
                    if(bytes.length>MAX_SQL_BYTES)throw new IllegalArgumentException("SQL asset too large");
                    String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                    String state=null,oldSha=null;
                    try(PreparedStatement q=connection.prepareStatement(
                            "SELECT sha256,state FROM sibyl_migrations WHERE filename=?")) {
                        q.setString(1,op.path());
                        try(ResultSet rs=q.executeQuery()) {
                            if(rs.next()){oldSha=rs.getString(1);state=rs.getString(2);}
                        }
                    }
                    if(state!=null) {
                        if (!sha.equals(oldSha) || !"APPLIED".equals(state))
                            throw new IllegalStateException("Migration drift/incomplete DDL: "+op.path());
                        existing++;continue;
                    }
                    // Each migration asset is exactly one SQL statement, no DELIMITER or multiquery.
                    String sql=new String(bytes,StandardCharsets.UTF_8).strip();
                    if(sql.endsWith(";"))sql=sql.substring(0,sql.length()-1).strip();
                    if(sql.indexOf(';')>=0 || sql.isEmpty())
                        throw new IllegalArgumentException("One SQL statement per migration asset");
                    // Persist STARTED first: MySQL DDL is implicitly committed and not rollback-safe.
                    try(PreparedStatement started=connection.prepareStatement(
                        "INSERT INTO sibyl_migrations(filename,sha256,state) VALUES (?,?,'STARTED')")){
                        started.setString(1,op.path());started.setString(2,sha);started.executeUpdate();
                    }
                    try(Statement stmt=connection.createStatement()){stmt.execute(sql);}
                    try(PreparedStatement done=connection.prepareStatement(
                        "UPDATE sibyl_migrations SET state='APPLIED',applied_at=CURRENT_TIMESTAMP WHERE filename=?")){
                        done.setString(1,op.path());done.executeUpdate();
                    }
                    applied++;
                }
            } finally {
                try(PreparedStatement unlock=connection.prepareStatement("SELECT RELEASE_LOCK(?)")){
                    unlock.setString(1,lockKey);unlock.execute();
                }
            }
        }
        return new Result(applied,existing);
    }
}
