package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A short message scrolled across the member dashboard's marquee banner - admin-managed, member-facing
 *  only (never shown on admin pages). Several published ones can run at once, each its own scrolling
 *  item, same pattern as GAT 2027's own news marquee. */
@Entity @Table(name = "announcements") @Getter @Setter
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, columnDefinition = "text") private String message;
    @Column(nullable = false) private boolean published = true;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);
}
