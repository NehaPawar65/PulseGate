package com.pulsegate.repository;

import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RequestEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestEventRepository extends JpaRepository<RequestEvent, Long> {
    List<RequestEvent> findTop100ByOrderByTimestampDesc();
    long countByDecision(Decision decision);
}
