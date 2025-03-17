package com.chihuahua.sobok.member;

import com.chihuahua.sobok.account.Account;
import com.chihuahua.sobok.jwt.JwtUtil;
import com.chihuahua.sobok.member.point.*;
import com.chihuahua.sobok.routine.todo.Todo;
import com.chihuahua.sobok.routine.todo.TodoDto;
import com.chihuahua.sobok.routine.todo.TodoRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PremiumRepository premiumRepository;
    private final PointLogService pointLogService;
    private final JwtUtil jwtUtil;
    private final TodoRepository todoRepository;

    public boolean isEmailDuplicated(String email) {
        return memberRepository.existsByEmail(email);
    }

    public boolean isPhoneNumberDuplicated(String phoneNumber) {
        return memberRepository.existsByPhoneNumber(phoneNumber);
    }

    public boolean isUsernameDuplicated(String username) {
        return memberRepository.existsByUsername(username);
    }

    public boolean isDisplayNameDuplicated(String displayName) {
        return memberRepository.existsByDisplayName(displayName);
    }

    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    // MemberController에서 유저 정보 조회
    public Map<String, Object> getUserInfo(Member member) {
        Map<String, Object> response = new HashMap<>();
        response.put("username", member.getUsername());
        response.put("id", member.getId());
        response.put("name", member.getName());
        response.put("displayName", member.getDisplayName());
        response.put("point", member.getPoint());
        response.put("email", member.getEmail());
        response.put("phoneNumber", member.getPhoneNumber());
        response.put("birth", member.getBirth());
        response.put("isPremium", member.getIsPremium());
        response.put("totalAchievedTime", member.getTotalAchievedTime());
        response.put("totalAccountBalance", member.getTotalAccountBalance());
        response.put("weeklyRoutineTime", member.getWeeklyRoutineTime());
        if(member.getIsPremium()) {
            Premium premium = premiumRepository.findByMemberAndEndAtAfter(member, LocalDate.now())
                    .orElseThrow(() -> new IllegalArgumentException("프리미엄 정보를 찾을 수 없습니다."));
            response.put("premiumEndAt", premium.getEndAt());
        }
        response.put("message", "유저 정보 조회 성공");
        return response;
    }

    @Transactional
    public Member saveOrUpdate(Member member) {
        var result = memberRepository.findByUsername(member.getUsername());
        if (result.isPresent()) {
            Member existingMember = result.get();
            existingMember.setName(member.getName());
            existingMember.setEmail(member.getEmail());
            existingMember.setBirth(member.getBirth());
            return memberRepository.save(existingMember);
        } else {
            return memberRepository.save(member);
        }
    }

    public void setAccessCookie(HttpServletResponse response, String accessToken) {
        // HttpOnly 쿠키 설정
        Cookie accessTokenCookie = new Cookie("accessToken", accessToken);
        accessTokenCookie.setHttpOnly(true);
//            refreshTokenCookie.setSecure(true); // HTTPS에서만 전송
        accessTokenCookie.setPath("/"); // 전체 경로에서 사용 가능
        accessTokenCookie.setMaxAge(15 * 60); // 15분 유효
        response.addCookie(accessTokenCookie);
    }

    public void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        // HttpOnly 쿠키 설정
        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
//            refreshTokenCookie.setSecure(true); // HTTPS에서만 전송
        refreshTokenCookie.setPath("/"); // 전체 경로에서 사용 가능
        refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60); // 7일 유효
        response.addCookie(refreshTokenCookie);

    }

    // 쿠키 제거
    public void removeCookie(HttpServletResponse response, String cookieName) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0); // 쿠키 제거
        response.addCookie(cookie);
    }

    // member로 로그인 성공 시 응답 바디
    public Map<String, Object> memberLoginSuccess(MemberLoginDto memberLoginDto, String jwt) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("accessToken", jwt);
        responseBody.put("username", memberLoginDto.getUsername()); //리턴으로 보내줄 거
        responseBody.put("message", "로그인 성공");
        responseBody.put("timestamp", LocalDateTime.now());
        responseBody.put("status", HttpStatus.OK.value());
        return responseBody;
    }

    public void upgradeToPremium(Member member) {
        Integer point = member.getPoint();
        Integer premiumPrice = member.getPremiumPrice();
        if(point < premiumPrice) {
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }
        PointLog pointLog = new PointLog();
        pointLog.setMember(member);
        pointLog.setPoint(-premiumPrice);
        pointLog.setBalance(point - premiumPrice);
        pointLog.setCategory("구독권 구매");
        pointLog.setCreatedAt(LocalDateTime.now());

        member.setPoint(point - premiumPrice);
        member.setIsPremium(true);

        Premium premium = premiumRepository.findByMemberAndEndAtAfter(member, LocalDate.now())
                .orElseGet(() -> {
                    Premium newPremium = new Premium();
                    newPremium.setMember(member);
                    return newPremium;
                });
        premium.setStartAt(LocalDate.now());
        premium.setEndAt(LocalDate.now().plusMonths(1));
//        premium.setIsAutoRenewal(false);
        premiumRepository.save(premium);
        memberRepository.save(member);
        pointLogService.save(pointLog);
    }

    @Transactional
    public Member getMember() {
        String token = jwtUtil.extractAccessTokenFromRequestHeader();
        System.out.println("getMember에서의 token = " + token);
        if(!jwtUtil.validateToken(token)) {
            System.out.println("토큰이 유효하지 않습니다.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다.");
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        System.out.println("getMember에서의 username = " + username);
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
    }

    public Integer calculatePremiumPrice(Member member) {
        List<Account> accounts = member.getAccounts();
        long totalTimeOfAccounts = accounts.stream()
                .mapToLong(Account::getTime)
                .sum();
        return (int) (totalTimeOfAccounts * 0.9);
    }

    public void updateOrSaveLinkApps(Member member, List<String> linkApps) {
        member.setLinkApps(linkApps);
        memberRepository.save(member);
    }

    public Map<String, Object> getLinkApps(Member member) {
        Map<String, Object> response = new HashMap<>();
        response.put("linkApps", member.getLinkApps());
        response.put("message", "연동 앱 조회 성공");
        return response;
    }

    public List<PremiumResponseDto> getPremiumLog(Member member) {
        List<Premium> premiumList = premiumRepository.findByMember(member);
        return premiumList.stream()
                .map(premium -> {
                    PremiumResponseDto premiumResponseDto = new PremiumResponseDto();
                    premiumResponseDto.setStartAt(premium.getStartAt());
                    premiumResponseDto.setEndAt(premium.getEndAt());
                    return premiumResponseDto;
                }).collect(Collectors.toList());
    }

    public List<TodoDto> getTodosByLinkApp(Member member, String linkApp) {
        List<Todo> todos = todoRepository.findAllByMemberAndLinkApp(member, linkApp);
        return todos.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    private TodoDto convertToDto(Todo todo) {
        TodoDto todoDto = new TodoDto();
        todoDto.setId(todo.getId());
        todoDto.setTitle(todo.getTitle());
        todoDto.setCategory(todo.getCategory());
        todoDto.setStartTime(todo.getStartTime());
        todoDto.setEndTime(todo.getEndTime());
        todoDto.setLinkApp(todo.getLinkApp());
        todoDto.setRoutineId(todo.getRoutine().getId());
        return todoDto;
    }
}


