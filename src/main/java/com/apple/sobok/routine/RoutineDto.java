package com.apple.sobok.routine;


import com.apple.sobok.routine.todo.TodoDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
public class RoutineDto {
    private Long accountId;
    private String title;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long duration;
    private List<String> days; // 요일 리스트(월 ~ 일)
    private List<TodoDto> todos;
}
