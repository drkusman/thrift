package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.Announcement;

public record AnnouncementDto(Long id, String message, boolean published, String createdAt) {
    public static AnnouncementDto of(Announcement a) {
        return new AnnouncementDto(a.getId(), a.getMessage(), a.isPublished(), a.getCreatedAt().toString());
    }
}
