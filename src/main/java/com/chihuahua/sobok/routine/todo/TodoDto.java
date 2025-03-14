package com.chihuahua.sobok.routine.todo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class TodoDto {
    private Long id;
    private String title;
    private String category;
    private LocalTime startTime;
    private LocalTime endTime;
    private String linkApp;
    private Long routineId;
}
