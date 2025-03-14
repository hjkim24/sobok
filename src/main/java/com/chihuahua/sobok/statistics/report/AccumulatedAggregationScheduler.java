package com.chihuahua.sobok.statistics.report;

import com.chihuahua.sobok.account.AccountLog;
import com.chihuahua.sobok.account.AccountLogRepository;
import com.chihuahua.sobok.member.Member;
import com.chihuahua.sobok.member.MemberRepository;
import com.chihuahua.sobok.routine.Routine;
import com.chihuahua.sobok.routine.RoutineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Profile("prod")
public class AccumulatedAggregationScheduler {

    private final MemberRepository memberRepository;
    private final AccountLogRepository accountLogRepository;
    private final RoutineRepository routineRepository;
    private final MonthlyUserReportRepository monthlyUserReportRepository;

    @Scheduled(cron = "0 0 0 L * *")
    public void aggregationMonthlyUserAccumulation() {
        String month = YearMonth.now().toString();
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = YearMonth.now().atEndOfMonth().atTime(23, 59, 59);
        List<Member> members = memberRepository.findAll();
        for(Member member: members){
            long totalDepositTime = member.getAccounts().stream()
                    .flatMap(account -> accountLogRepository.findByAccountAndCreatedAtBetween(account, startOfMonth, endOfMonth).stream())
                    .mapToLong(AccountLog::getDepositTime)
                    .sum();
            MonthlyUserReport monthlyUserReport = new MonthlyUserReport();
            monthlyUserReport.setMemberId(member.getId());
            monthlyUserReport.setTargetYearMonth(month);
            monthlyUserReport.setTotalAccumulatedTime(totalDepositTime);
            monthlyUserReport.setAverageAccumulatedTime(totalDepositTime / countDaysInMonth(getMemberDays(member), month));
            monthlyUserReportRepository.save(monthlyUserReport);
        }
    }

    public Set<String> getMemberDays(Member member) {
        List<Routine> routines = routineRepository.findByMemberAndAccountIsExpired(member, false);
        return routines.stream()
                .flatMap(routine -> routine.getDays().stream())
                .collect(Collectors.toSet());
    }

    public int countDaysInMonth(Set<String> days, String yearMonth) {
        YearMonth yearMonthObj = YearMonth.parse(yearMonth);
        int count = 0;
        // 1일부터 해당 달의 마지막 날까지 반복
        for (int day = 1; day <= yearMonthObj.lengthOfMonth(); day++) {
            LocalDate date = yearMonthObj.atDay(day);
            // 요일은 영어 대문자로 나오므로
            String dayName = date.getDayOfWeek().name();
            if (days.contains(dayName)) {
                count++;
            }
        }
        return count;
    }
}
