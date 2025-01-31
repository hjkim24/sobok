package com.apple.sobok.account;

import com.apple.sobok.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByMemberAndIsExpired(Member member, Boolean isExpired);

    Optional<Account> findByMemberAndId(Member member, Long id);

    List<Account> findByMemberAndIsValid(Member member, Boolean isValid);

    List<Account> findByIsExpired(Boolean isExpired);

}
