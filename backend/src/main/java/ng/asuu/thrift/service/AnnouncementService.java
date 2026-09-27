package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.Announcement;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.repo.AnnouncementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AnnouncementService {
    private final AnnouncementRepository repo;

    public AnnouncementService(AnnouncementRepository repo) {
        this.repo = repo;
    }

    public List<Announcement> all() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    /** Every currently-published message, for the member dashboard's marquee. */
    public List<Announcement> active() {
        return repo.findByPublishedTrueOrderByCreatedAtDesc();
    }

    @Transactional
    public Announcement create(Member admin, String message) {
        Announcement a = new Announcement();
        a.setMessage(message);
        a.setCreatedBy(admin.getId());
        return repo.save(a);
    }

    @Transactional
    public Announcement update(Long id, String message, boolean published) {
        Announcement a = require(id);
        a.setMessage(message);
        a.setPublished(published);
        a.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return repo.save(a);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }

    private Announcement require(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
    }
}
