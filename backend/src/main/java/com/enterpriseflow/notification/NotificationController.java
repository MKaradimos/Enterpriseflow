package com.enterpriseflow.notification;

import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List the caller's notifications, newest first")
    public PageResponse<NotificationResponse> list(CurrentUser user,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        // Sorting is fixed: a feed has one meaningful order.
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationService.list(user.id(), unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications, for the header badge")
    public Map<String, Long> unreadCount(CurrentUser user) {
        return Map.of("count", notificationService.unreadCount(user.id()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse markRead(CurrentUser user, @PathVariable UUID id) {
        return notificationService.markRead(user.id(), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public Map<String, Integer> markAllRead(CurrentUser user) {
        return Map.of("updated", notificationService.markAllRead(user.id()));
    }
}
