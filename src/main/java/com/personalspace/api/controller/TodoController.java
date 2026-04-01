package com.personalspace.api.controller;

import com.personalspace.api.dto.request.CreateTodoRequest;
import com.personalspace.api.dto.request.UpdateTodoRequest;
import com.personalspace.api.dto.response.PaginatedResponse;
import com.personalspace.api.dto.response.TodoResponse;
import com.personalspace.api.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/todos")
public class TodoController {
    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @PostMapping
    public ResponseEntity<TodoResponse> createTodo(
        Principal principal,
        @Valid @RequestBody CreateTodoRequest request
    ) {
        TodoResponse response = todoService.createTodo(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<TodoResponse>> getTodos(
        Principal principal,
        @RequestParam(defaultValue = "false") boolean archived,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        PaginatedResponse<TodoResponse> response = todoService.getTodos(principal.getName(), archived, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponse> getTodo(
        Principal principal,
        @PathVariable UUID id
    ) {
        TodoResponse response = todoService.getTodo(principal.getName(), id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponse> updateTodo(
        Principal principal,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTodoRequest request
    ) {
        TodoResponse response = todoService.updateTodo(principal.getName(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(
        Principal principal,
        @PathVariable UUID id
    ) {
        todoService.deleteTodo(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<PaginatedResponse<TodoResponse>> searchTodos(
        Principal principal,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "false") boolean archived,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        PaginatedResponse<TodoResponse> response = todoService.searchTodos(principal.getName(), q, archived, page, size);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TodoResponse> toggleComplete(
        Principal principal,
        @PathVariable UUID id
    ) {
        TodoResponse response = todoService.toggleComplete(principal.getName(), id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<TodoResponse> toggleArchive(
        Principal principal,
        @PathVariable UUID id
    ) {
        TodoResponse response = todoService.toggleArchive(principal.getName(), id);
        return ResponseEntity.ok(response);
    }
}