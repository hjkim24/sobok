package com.apple.sobok.statistics.report;

import com.apple.sobok.member.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class SnowCard {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private String yearMonth;

    private String snowCard;
}
