package ng.asuu.thrift.web;

import ng.asuu.thrift.service.DatabaseBackupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/backup")
public class AdminBackupController {
    private final DatabaseBackupService backupService;
    private final String datasourceUrl;
    private final String username;

    public AdminBackupController(
            DatabaseBackupService backupService,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String username) {
        this.backupService = backupService;
        this.datasourceUrl = datasourceUrl;
        this.username = username;
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download() {
        DatabaseBackupService.Backup backup = backupService.dump();
        return FileDownload.sql(backup.content(), backup.filename());
    }

    public record ConnectionInfo(String host, String port, String dbName, String username) {}

    /** Read-only - just tells the admin what to plug into pg_dump/psql when restoring by hand
     *  over SSH. Deliberately never returns the password; the admin already has that from the
     *  server's own application.yml. */
    @GetMapping("/connection-info")
    public ConnectionInfo connectionInfo() {
        String prefix = "jdbc:postgresql://";
        String rest = datasourceUrl.substring(prefix.length());
        int slash = rest.indexOf('/');
        String hostPort = rest.substring(0, slash);
        String dbPart = rest.substring(slash + 1);
        int q = dbPart.indexOf('?');
        String dbName = q >= 0 ? dbPart.substring(0, q) : dbPart;
        int colon = hostPort.indexOf(':');
        String host = colon >= 0 ? hostPort.substring(0, colon) : hostPort;
        String port = colon >= 0 ? hostPort.substring(colon + 1) : "5432";
        return new ConnectionInfo(host, port, dbName, username);
    }
}
