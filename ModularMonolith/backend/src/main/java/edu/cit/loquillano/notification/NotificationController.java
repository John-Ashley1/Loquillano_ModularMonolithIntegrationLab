package edu.cit.loquillano.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<NotificationDTO> listNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(n -> new NotificationDTO(n.getNotificationId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
