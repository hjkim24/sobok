package com.chihuahua.sobok.member.spareTIme;

import com.chihuahua.sobok.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

@SuppressWarnings("SpringDataRepositoryMethodParametersInspection")
public interface SpareTimeRepository extends JpaRepository<SpareTime, Long> {
    List<SpareTime> findByMember(Member member);

    List<SpareTime> findByMemberAndDaysContaining(Member member, List<String> days);
}
