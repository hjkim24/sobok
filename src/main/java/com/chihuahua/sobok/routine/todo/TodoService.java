package com.chihuahua.sobok.routine.todo;



import com.chihuahua.sobok.account.Account;
import com.chihuahua.sobok.account.AccountService;
import com.chihuahua.sobok.member.Member;
import com.chihuahua.sobok.member.MemberService;
import com.chihuahua.sobok.routine.Routine;
import com.chihuahua.sobok.routine.RoutineLog;
import com.chihuahua.sobok.routine.RoutineLogRepository;
import com.chihuahua.sobok.routine.RoutineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;
    private final TodoLogRepository todoLogRepository;
    private final RoutineLogRepository routineLogRepository;
    private final RoutineRepository routineRepository;
    private final AccountService accountService;
    private final MemberService memberService;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ResponseEntity<?> startTodo(Member member, Long todoId) {
        try {
            Todo todo = todoRepository.findByMemberAndId(member, todoId).orElseThrow(
                    () -> new IllegalArgumentException("해당 ID의 할 일이 존재하지 않습니다."));

            TodoLog todoLog = new TodoLog();
            todoLog.setTodo(todo);
            todoLog.setStartTime(LocalDateTime.now());
            todoLog.setIsCompleted(false);


            // 시간순으로 정렬된 할 일 목록 가져오기
            List<Todo> relatedTodos = todo.getRoutine().getTodos().stream()
            .sorted(Comparator.comparing(Todo::getStartTime))
            .toList();

            // 가장 빠른 시작 시간을 가진 할 일 확인
            boolean isFirstTodo = relatedTodos.getFirst().getId().equals(todoId);

            // 응답용 맵 초기화
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("message", "할 일이 시작되었습니다.");
            responseMap.put("todoLogId", todoLog.getId());

            // 첫 할 일인 경우 루틴 로그 생성
            if(isFirstTodo) {
                RoutineLog routineLog = new RoutineLog();
                routineLog.setRoutine(todo.getRoutine());
                routineLog.setStartTime(LocalDateTime.now());
                routineLog.setIsCompleted(false);
                RoutineLog currentRoutineLog = routineLogRepository.save(routineLog);
                // todoLog에 루틴 로그 ID 설정
                todoLog.setRoutineLogId(currentRoutineLog.getId());
            
            // 루틴 로그 ID도 응답에 추가
            responseMap.put("routineLogId", routineLog.getId());
            responseMap.put("isFirstTodo", true);
            } else {
                // 진행 중인 루틴 로그 가져오기
                RoutineLog currentRoutineLog = routineLogRepository.findByRoutineIdAndIsCompleted(
                        todo.getRoutine().getId(), false).orElseThrow(
                        () -> new IllegalArgumentException("해당 루틴의 진행 중인 로그가 존재하지 않습니다."));
                todoLog.setRoutineLogId(currentRoutineLog.getId());
                responseMap.put("isFirstTodo", false);
            }

        return ResponseEntity.ok(responseMap);

    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "할 일 시작에 실패했습니다: " + e.getMessage()));
    }
}

    @Transactional
    public ResponseEntity<?> endTodo(Long todoLogId, Long duration) {
    try {
        TodoLog todoLog = todoLogRepository.findById(todoLogId).orElseThrow(
                () -> new IllegalArgumentException("해당 ID의 할 일 로그가 존재하지 않습니다."));

        todoLog.setEndTime(LocalDateTime.now());
        todoLog.setDuration(duration);
        todoLog.setIsCompleted(true);

        Todo todo = todoLog.getTodo();
        Routine routine = todo.getRoutine();

        // 할 일 완료 시 적금에 시간 적립 및 로그 생성
        Member member = memberService.getMember();
        Long accountId = routine.getAccount().getId();

        accountService.depositAccount(member, accountId, Math.toIntExact(duration));

        todoLogRepository.save(todoLog);

        // 입력받은 Duration이 할 일 duration의 90퍼센트를 넘는지 확인
        todo.setIsCompleted(duration >= todo.getDuration() * 0.9);
        
        // 시간순으로 정렬된 할 일 목록 가져오기
        List<Todo> relatedTodos = todo.getRoutine().getTodos().stream()
            .sorted(Comparator.comparing(Todo::getStartTime))
            .toList();

        // 가장 늦은 시작 시간을 가진 할 일 확인
        boolean isLastTodo = relatedTodos.getLast().getId().equals(todo.getId());

        // 응답용 맵 초기화
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("message", "할 일이 완료되었습니다.");
        responseMap.put("isLastTodo", isLastTodo);

        // 마지막 할 일인 경우 루틴 로그 종료 처리
        if(isLastTodo) {
            RoutineLog routineLog = routineLogRepository.findByRoutineAndIsCompleted(todo.getRoutine(), false).orElseThrow(
                    () -> new IllegalArgumentException("해당 루틴의 진행 중인 로그가 존재하지 않습니다."));
            
            routineLog.setEndTime(LocalDateTime.now());
            routineLog.setDuration(todoLogRepository.findByRoutineLogId(routineLog.getId())
                    .stream()
                    .mapToLong(TodoLog::getDuration).sum());
            routineLog.setIsCompleted(true);
            routineLogRepository.save(routineLog);

            // 뭐라도 하긴 했으면 isAchieved를 true로 설정
            if(!routine.getIsAchieved()) {
                routine.setIsAchieved(true);
            }

            // 루틴의 모든 할 일이 completed 상태인지 확인(90% 이상 완료된 경우)
            routine.setIsCompleted(routine.getTodos().stream().allMatch(Todo::getIsCompleted));
            routineRepository.save(routine);

            // 루틴 로그 정보 응답에 추가
            responseMap.put("routineLogId", routineLog.getId());
            responseMap.put("routineDuration", routineLog.getDuration());
            responseMap.put("routineCompleted", true);
        }

        return ResponseEntity.ok(responseMap);
        
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "할 일 완료에 실패했습니다: " + e.getMessage()));
    }
}

    public ResponseEntity<?> getTodoCategory() {
        Map<String, String> response = new HashMap<>();
        response.put("english", "영어");
        response.put("math", "수학");
        response.put("science", "과학");
        response.put("history", "역사");
        response.put("art", "미술");
        response.put("music", "음악");
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> getTodayTodos() {
        List<Todo> todos = todoRepository.findByMemberAndDay(memberService.getMember(), LocalDateTime.now().getDayOfWeek().name());
        List<TodoDto> todoDtos = todos.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(todoDtos);
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

    public ResponseEntity<?> getClosestTodo(Member member) {
        LocalDateTime now = LocalDateTime.now();
        String today = now.getDayOfWeek().toString();

        // 오늘의 모든 할 일을 가져온 후 현재 시간 이후의 것들만 필터링
        List<Todo> todayTodos = todoRepository.findByMemberAndDay(member, today)
                .stream()
                .filter(todo -> !todo.getIsCompleted())
                .filter(todo -> {
                    LocalDateTime todoStartTime = LocalDateTime.of(now.toLocalDate(), todo.getStartTime());
                    return todoStartTime.isAfter(now);
                })
                .sorted(Comparator.comparing(todo ->
                        LocalDateTime.of(now.toLocalDate(), todo.getStartTime())
                ))
                .toList();

        if (todayTodos.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "오늘 남은 할 일이 없습니다."));
        }

        // 가장 가까운 할 일 반환
        Todo closestTodo = todayTodos.getFirst();
        return ResponseEntity.ok(convertToDto(closestTodo));
    }

    public ResponseEntity<?> getAllTodos() {
        List<Todo> todos = todoRepository.findAllByMember(memberService.getMember());
        List<TodoDto> todoDtos = todos.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(todoDtos);
    }

    @Transactional
    public ResponseEntity<?> updateTodo(TodoDto todoDto) {
        Member member = memberService.getMember();
        Optional<Todo> todo = todoRepository.findById(todoDto.getId());
        if (todo.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "message", "해당 ID의 할 일이 존재하지 않습니다."
        ));
    }
    Todo updatedTodo = todo.get();
    Routine routine = updatedTodo.getRoutine();
    List<Todo> todos = routine.getTodos();
    Account account = routine.getAccount();
    updatedTodo.setTitle(todoDto.getTitle());
    updatedTodo.setCategory(todoDto.getCategory());

    // Category 테이블 추가
    if(categoryRepository.findByMemberAndCategory(member, todoDto.getCategory()).isEmpty()) {
        Category category = new Category();
        category.setMember(member);
        category.setCategory(todoDto.getCategory());
        category.setCreatedAt(LocalDateTime.now());
        categoryRepository.save(category);
    }

    // 기존의 다른 할 일과의 중복 체크
    List<Todo> existingTodos = todoRepository.findAllByMemberAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            member, todoDto.getEndTime(), todoDto.getStartTime());
    
    // 자신을 제외하고 중복되는 할일이 있는지 확인
    existingTodos = existingTodos.stream()
            .filter(t -> !t.getId().equals(todoDto.getId()))
            .toList();
            
    if (!existingTodos.isEmpty()) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "기존의 다른 할 일과 시간이 겹칩니다.");
        
        // 중복되는 할 일 정보를 응답에 포함
        List<Map<String, Object>> conflictingTodos = existingTodos.stream()
                .map(t -> {
                    Map<String, Object> todoInfo = new HashMap<>();
                    todoInfo.put("id", t.getId());
                    todoInfo.put("title", t.getTitle());
                    todoInfo.put("startTime", t.getStartTime().toString());
                    todoInfo.put("endTime", t.getEndTime().toString());
                    todoInfo.put("category", t.getCategory());
                    if (t.getRoutine() != null) {
                        todoInfo.put("routineId", t.getRoutine().getId());
                        todoInfo.put("routineTitle", t.getRoutine().getTitle());
                    }
                    return todoInfo;
                })
                .collect(Collectors.toList());
        
        response.put("conflictingTodos", conflictingTodos);
        
        // 409 Conflict: 요청이 현재 서버의 상태와 충돌할 때 사용
        // TransactionSystemException 방지를 위해 트랜잭션을 롤백하지 않고 바로 종료
        return ResponseEntity.status(409).body(response);
    }

    updatedTodo.setStartTime(todoDto.getStartTime());
    updatedTodo.setEndTime(todoDto.getEndTime());
    updatedTodo.setDuration(Duration.between(todoDto.getStartTime(), todoDto.getEndTime()).toMinutes());
    updatedTodo.setLinkApp(todoDto.getLinkApp());
    updatedTodo.setIsCompleted(false);

    // 루틴의 시작 시간과 종료 시간을 첫 번째 할일의 시작 시간과 마지막 할일의 종료 시간으로 설정
    routine.setStartTime(todos.getFirst().getStartTime());
    routine.setEndTime(todos.getLast().getEndTime());

    // 루틴의 duration을 할일들의 duration 합으로 설정
    long totalDuration = todos.stream().mapToLong(Todo::getDuration).sum();
    routine.setDuration(totalDuration);

    routineRepository.save(routine);
    todoRepository.save(updatedTodo);

    //적금 활성화 여부 체크
    if(account != null) {
        accountService.validateAccount(account);
    }

    return ResponseEntity.ok(Map.of("message", "할 일이 업데이트되었습니다."));
}

    @Transactional
    public ResponseEntity<?> deleteTodo(Long todoId) {
        Optional<Todo> todo = todoRepository.findById(todoId);
        if (todo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "해당 ID의 할 일이 존재하지 않습니다."));
        }
        Todo deletedTodo = todo.get();
        Routine routine = deletedTodo.getRoutine();
        Account account = routine.getAccount();

        // 루틴 할 일 삭제(헬퍼 메서드)
        routine.removeTodo(deletedTodo);
        routineRepository.save(routine);
        todoRepository.delete(deletedTodo);

        List<Todo> todos = routine.getTodos();

        // 루틴의 시작 시간과 종료 시간을 첫 번째 할일의 시작 시간과 마지막 할일의 종료 시간으로 설정
        if (!todos.isEmpty()) {
            routine.setStartTime(todos.getFirst().getStartTime());
            routine.setEndTime(todos.getLast().getEndTime());

            // 루틴의 duration을 할일들의 duration 합으로 설정
            long totalDuration = todos.stream().mapToLong(Todo::getDuration).sum();
            routine.setDuration(totalDuration);
        } else {
            routine.setStartTime(null);
            routine.setEndTime(null);
            routine.setDuration(0L);
        }

        routineRepository.save(routine);

        //적금 활성화 여부 체크
        if(account != null) {
            accountService.validateAccount(account);
        }

        return ResponseEntity.ok(Map.of("message", "할 일이 삭제되었습니다."));
    }

    @Transactional(readOnly = true)
    public boolean checkOverlap(Member member, OverlapTimeCheckDto overlapTimeCheckDto) {
        List<Todo> overlappingTodos = todoRepository.findOverlappingTodos(
                member,
                overlapTimeCheckDto.getDays(),
                overlapTimeCheckDto.getStartTime(),
                overlapTimeCheckDto.getEndTime()
        );
        return !overlappingTodos.isEmpty();
    }



//    public boolean checkOverlap(Member member, TimeDto timeDto) {
//        // 기존의 다른 할 일과의 중복 체크 (요일 반영)
//        List<Todo> existingTodos = todoRepository.findAllByMemberAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
//                member, timeDto.getEndTime(), timeDto.getStartTime());
//        return !existingTodos.isEmpty();
//    }
}