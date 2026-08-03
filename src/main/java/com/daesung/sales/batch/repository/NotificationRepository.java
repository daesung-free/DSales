package com.daesung.sales.batch.repository;

import com.daesung.sales.batch.entity.Notification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 중복 적재 방지 — 배치 재실행 시 같은 알림을 다시 만들지 않는다. */
    Optional<Notification> findByDedupKey(String dedupKey);

    Page<Notification> findByReadYnOrderByNotifyDateDescIdDesc(boolean readYn, Pageable pageable);

    Page<Notification> findAllByOrderByNotifyDateDescIdDesc(Pageable pageable);
}
