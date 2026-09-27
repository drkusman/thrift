package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.AnnouncementService;
import ng.asuu.thrift.web.dto.AnnouncementDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {
    private final AnnouncementService service;

    public AdminAnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnnouncementDto> all() {
        return service.all().stream().map(AnnouncementDto::of).toList();
    }

    @PostMapping
    public AnnouncementDto create(@AuthenticationPrincipal MemberPrincipal admin, @RequestBody CreateRequest req) {
        return AnnouncementDto.of(service.create(admin.getMember(), req.message()));
    }

    @PutMapping("/{id}")
    public AnnouncementDto update(@PathVariable Long id, @RequestBody UpdateRequest req) {
        return AnnouncementDto.of(service.update(id, req.message(), req.published()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    public record CreateRequest(String message) {}

    public record UpdateRequest(String message, boolean published) {}
}
