package com.apple.sobok.member.point;

import com.apple.sobok.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PointLogRepository extends JpaRepository<PointLog, Long> {
    List<PointLog> findByMember(Member member);

    List<PointLog> findByMemberAndCreatedAtBetween(Member member, LocalDateTime createdAtAfter, LocalDateTime createdAtBefore);
}
