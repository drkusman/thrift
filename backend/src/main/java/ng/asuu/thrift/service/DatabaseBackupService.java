package ng.asuu.thrift.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/** Dumps the whole database with the server's own pg_dump binary (must be on PATH - it ships
 *  alongside the Postgres server package on the production host) rather than reimplementing a
 *  schema+data export in Java. */
@Service
public class DatabaseBackupService {
    private final String host;
    private final String port;
    private final String dbName;
    private final String username;
    private final String password;

    public DatabaseBackupService(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        String[] parsed = parseJdbcUrl(url);
        this.host = parsed[0];
        this.port = parsed[1];
        this.dbName = parsed[2];
        this.username = username;
        this.password = password;
    }

    public record Backup(String filename, byte[] content) {}

    public Backup dump() {
        Path tempFile;
        try {
            tempFile = Files.createTempFile("thrift-backup-", ".sql");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create a temp file for the backup");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("pg_dump",
                    "-h", host, "-p", port, "-U", username, "-d", dbName,
                    "--no-password", "-f", tempFile.toString());
            pb.environment().put("PGPASSWORD", password);
            Process process = pb.start();
            String stderr;
            try (var err = process.getErrorStream()) {
                stderr = new String(err.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database backup timed out after 5 minutes");
            }
            if (process.exitValue() != 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "pg_dump failed: " + stderr.trim());
            }
            byte[] content = Files.readAllBytes(tempFile);
            String filename = "thrift-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".sql";
            return new Backup(filename, content);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not run pg_dump - is it installed and on the server's PATH? (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database backup was interrupted");
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // best-effort cleanup of a temp file
            }
        }
    }

    private static String[] parseJdbcUrl(String url) {
        String prefix = "jdbc:postgresql://";
        if (!url.startsWith(prefix)) {
            throw new IllegalStateException("Unsupported datasource URL for backup: " + url);
        }
        String rest = url.substring(prefix.length());
        int slash = rest.indexOf('/');
        String hostPort = rest.substring(0, slash);
        String dbPart = rest.substring(slash + 1);
        int q = dbPart.indexOf('?');
        String dbName = q >= 0 ? dbPart.substring(0, q) : dbPart;
        int colon = hostPort.indexOf(':');
        String host = colon >= 0 ? hostPort.substring(0, colon) : hostPort;
        String port = colon >= 0 ? hostPort.substring(colon + 1) : "5432";
        return new String[]{host, port, dbName};
    }
}
