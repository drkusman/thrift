package ng.asuu.thrift.web;

import ng.asuu.thrift.service.AnnouncementService;
import ng.asuu.thrift.web.dto.AnnouncementDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The member-facing read side - any logged-in member sees every currently-published announcement,
 *  scrolled across their dashboard's marquee banner. Admin management lives in AdminAnnouncementController. */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnnouncementDto> active() {
        return service.active().stream().map(AnnouncementDto::of).toList();
    }
}
