package com.apple.sobok.routine;


import com.apple.sobok.account.Account;
import com.apple.sobok.account.AccountRepository;
import com.apple.sobok.account.AccountService;
import com.apple.sobok.member.Member;
import com.apple.sobok.routine.todo.Todo;
import com.apple.sobok.routine.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoutineService {

    private final AccountRepository accountRepository;
    private final RoutineRepository routineRepository;
    private final TodoRepository todoRepository;
    private final AccountService accountService;

    public void createRoutine(RoutineDto routineDto, Member member) {
        Routine routine = new Routine();
        Account account = accountRepository.findById(
                routineDto.getAccountId()).orElseThrow(
                () -> new IllegalArgumentException("해당 적금이 존재하지 않습니다."));
        routine.setMember(member);
        routine.setAccount(account);
        routine.setTitle(routineDto.getTitle());
        routine.setDays(routineDto.getDays());
        routine.setCreatedAt(LocalDateTime.now());
        routine.setIsSuspended(false);
        routine.setIsCompleted(false);
        routine.setIsEnded(false);
        routineRepository.save(routine);

        // Todo 생성 로직 추가
        if (routineDto.getTodos() != null && !routineDto.getTodos().isEmpty()) {
            List<Todo> todos = routineDto.getTodos().stream().map(todoDto -> {
                Todo todo = new Todo();
                todo.setRoutine(routine);
                todo.setTitle(todoDto.getTitle());
                todo.setStartTime(todoDto.getStartTime());
                todo.setEndTime(todoDto.getEndTime());
                todo.setDuration(Duration.between(todoDto.getStartTime(), todoDto.getEndTime()).toMinutes());
                todo.setLinkApp(todoDto.getLinkApp());
                todo.setIsCompleted(false);
                return todo;
            }).collect(Collectors.toList());

            // 루틴의 시작 시간과 종료 시간을 첫 번째 할일의 시작 시간과 마지막 할일의 종료 시간으로 설정
            routine.setStartTime(todos.getFirst().getStartTime());
            routine.setEndTime(todos.getLast().getEndTime());

            // 루틴의 duration을 할일들의 duration 합으로 설정
            long totalDuration = todos.stream().mapToLong(Todo::getDuration).sum();
            routine.setDuration(totalDuration);

            routineRepository.save(routine);
            todoRepository.saveAll(todos);

        }

        //적금 활성화 여부 체크
        accountService.validateAccount(account);
    }

    public void updateRoutine(RoutineDto routineDto, Member member, Long routineId) {
        var result = routineRepository.findByMemberAndId(member, routineId);
        if(result.isEmpty()) {
            throw new IllegalArgumentException("해당 루틴이 존재하지 않습니다.");
        }
        Routine routine = result.get();
        Account account = accountRepository.findById(
                routineDto.getAccountId()).orElseThrow(
                () -> new IllegalArgumentException("해당 적금이 존재하지 않습니다."));
        routine.setAccount(account);
        routine.setTitle(routineDto.getTitle());
        routine.setDays(routineDto.getDays());
        routineRepository.save(routine);

        // Todo 업데이트 로직 추가
        List<Todo> todos = todoRepository.findByRoutine(routine);
        todoRepository.deleteAll(todos);
        if (routineDto.getTodos() != null && !routineDto.getTodos().isEmpty()) {
            todos = routineDto.getTodos().stream().map(todoDto -> {
                Todo todo = new Todo();
                todo.setRoutine(routine);
                todo.setTitle(todoDto.getTitle());
                todo.setStartTime(todoDto.getStartTime());
                todo.setEndTime(todoDto.getEndTime());
                todo.setDuration(Duration.between(todoDto.getStartTime(), todoDto.getEndTime()).toMinutes());
                todo.setLinkApp(todoDto.getLinkApp());
                todo.setIsCompleted(false);
                return todo;
            }).collect(Collectors.toList());

            // 루틴의 시작 시간과 종료 시간을 첫 번째 할일의 시작 시간과 마지막 할일의 종료 시간으로 설정
            routine.setStartTime(todos.getFirst().getStartTime());
            routine.setEndTime(todos.getLast().getEndTime());

            // 루틴의 duration을 할일들의 duration 합으로 설정
            long totalDuration = todos.stream().mapToLong(Todo::getDuration).sum();
            routine.setDuration(totalDuration);

            todoRepository.saveAll(todos);
        }

        //적금 활성화 여부 체크
        accountService.validateAccount(account);
    }

    public void deleteRoutine(Member member, Long routineId) {
        var result = routineRepository.findByMemberAndId(member, routineId);
        if(result.isEmpty()) {
            throw new IllegalArgumentException("해당 루틴이 존재하지 않습니다.");
        }
        Routine routine = result.get();
        Account account = routine.getAccount();
        routineRepository.delete(routine);

        // Todo 삭제 로직 추가
        List<Todo> todos = todoRepository.findByRoutine(routine);
        todoRepository.deleteAll(todos);

        //적금 활성화 여부 체크
        accountService.validateAccount(account);
    }

    public ResponseEntity<?> getTodayRoutine(Member member, String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate date = LocalDate.parse(dateString, formatter);
        String dayOfWeek = date.getDayOfWeek().toString();
        List<Routine> result = routineRepository.findByUserIdAndDay(member.getId(), dayOfWeek);
        if (result.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "오늘의 루틴이 없습니다."));
        }
        List<Map<String, Object>> routines = result.stream()
                .map(this::convertToMapCal)
                .toList();

        return ResponseEntity.ok(routines);
    }

    private Map<String, Object> convertToMapCal(Routine routine) {
        return Map.of(
                "title", routine.getTitle(),
                "accountTitle", routine.getAccount().getTitle(),
                "startTime", routine.getStartTime(),
                "endTime", routine.getEndTime(),
                "duration", routine.getDuration()
        );
    }

    public ResponseEntity<?> getAllRoutine(Member member) {
        List<Routine> result = routineRepository.findByMember(member);
        if (result.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "루틴이 없습니다."));
        }
        List<Map<String, Object>> routines = result.stream()
                .map(this::convertToMapList)
                .toList();

        return ResponseEntity.ok(routines);
    }

    private Map<String, Object> convertToMapList(Routine routine) {
        return Map.of(
                "title", routine.getTitle(),
                "accountTitle", routine.getAccount().getTitle(),
                "duration", routine.getDuration(),
                "isSuspended", routine.getIsSuspended()
        );
    }

    public ResponseEntity<?> getRoutine(Member member, Long routineId) {
        var result = routineRepository.findByMemberAndId(member, routineId);
        if (result.isPresent()) {
            return ResponseEntity.ok(convertToMap(result.get()));
        }
        return ResponseEntity.ok(Map.of("message", "해당 ID의 루틴이 없습니다."));
    }

    private Map<String, Object> convertToMap(Routine routine) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", routine.getId());
        response.put("accountTitle", routine.getAccount().getTitle());
        response.put("title", routine.getTitle());
        response.put("days", routine.getDays());
        response.put("startTime", routine.getStartTime());
        response.put("endTime", routine.getEndTime());
        response.put("duration", routine.getDuration());
        response.put("isSuspended", routine.getIsSuspended());
        response.put("isCompleted", routine.getIsCompleted());
        response.put("isEnded", routine.getIsEnded());
        response.put("todos", routine.getTodos().stream()
                .map(todo -> Map.of(
                        "title", todo.getTitle(),
                        "startTime", todo.getStartTime(),
                        "endTime", todo.getEndTime(),
                        "duration", todo.getDuration(),
                        "linkApp", todo.getLinkApp(),
                        "isCompleted", todo.getIsCompleted()
                ))
                .toList());
        return response;
    }

    public ResponseEntity<?> getTodayWillRoutine(Member member) {
        LocalDate date = LocalDate.now();
        String dayOfWeek = date.getDayOfWeek().toString();
        List<Routine> routines = routineRepository.findByUserIdAndDay(member.getId(), dayOfWeek);
        if (routines.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "오늘 완료하지 않은 루틴이 없습니다."));
        }
        List<Map<String, Object>> result = routines.stream()
                .filter(routine -> !routine.getIsCompleted())
                .map(this::convertToMapList)
                .toList();
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<?> getTodayDoneRoutine(Member member) {
        LocalDate date = LocalDate.now();
        String dayOfWeek = date.getDayOfWeek().toString();
        List<Routine> routines = routineRepository.findByUserIdAndDayCompleted(member.getId(), dayOfWeek);
        if (routines.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "오늘 완료한 루틴이 없습니다."));
        }
        List<Map<String, Object>> result = routines.stream()
                .map(this::convertToMapList)
                .toList();
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<?> getIsThereEndedRoutine(Member member) {
        List<Routine> routines = routineRepository.findByMemberAndIsEnded(member, true);
        if (routines.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "만료된 루틴이 없습니다."));
        }
        List<Map<String, Object>> result = routines.stream()
                .map(this::convertToMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<?> suspendRoutine(Member member, Long routineId) {
        var result = routineRepository.findByMemberAndId(member, routineId);
        if (result.isPresent()) {
            Routine routine = result.get();
            if(!routine.getIsSuspended()) {
                routine.setIsSuspended(true);
                //루틴 보류 시 적금 연결도 해제
                routine.setAccount(null);
                routineRepository.save(routine);
                return ResponseEntity.ok(Map.of("message", "루틴이 중단되었습니다."));
            }
            else {
                routine.setIsSuspended(false);
                routineRepository.save(routine);
                return ResponseEntity.ok(Map.of("message", "루틴이 재개되었습니다."));
            }

        }
        return ResponseEntity.ok(Map.of("message", "해당 ID의 루틴이 없습니다."));
    }



}
