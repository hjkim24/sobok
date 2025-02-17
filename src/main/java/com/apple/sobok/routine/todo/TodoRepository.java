package com.apple.sobok.routine.todo;

import com.apple.sobok.member.Member;
import com.apple.sobok.routine.Routine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    List<Todo> findByRoutine(Routine routine);
    @Query("SELECT t FROM Todo t WHERE t.routine.member = :member AND :day MEMBER OF t.routine.days")
    List<Todo> findByMemberAndDay(@Param("member") Member member, @Param("day") String day);
}
