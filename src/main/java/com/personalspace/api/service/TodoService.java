package com.personalspace.api.service;

import com.personalspace.api.dto.request.CreateTodoRequest;
import com.personalspace.api.dto.request.UpdateTodoRequest;
import com.personalspace.api.dto.response.PaginatedResponse;
import com.personalspace.api.dto.response.TodoResponse;
import com.personalspace.api.exception.ResourceNotFoundException;
import com.personalspace.api.model.entity.Todo;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.repository.TodoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TodoService {
    private final TodoRepository todoRepository;
    private final UserService userService;

    public TodoService(TodoRepository todoRepository, UserService userService) {
        this.todoRepository = todoRepository;
        this.userService = userService;
    }

    @Transactional
    public TodoResponse createTodo(String email, CreateTodoRequest request) {
        User user = userService.getUserByEmail(email);

        Todo todo = new Todo();
        todo.setTitle(request.title().trim());
        todo.setCompleted(false);
        todo.setArchived(false);
        todo.setUser(user);

        Todo saved = todoRepository.save(todo);
        return toTodoResponse(saved);
    }

    public TodoResponse getTodo(String email, UUID todoId) {
        User user = userService.getUserByEmail(email);
        Todo todo = todoRepository.findByIdAndUser(todoId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Todo not found with id: " + todoId));
        return toTodoResponse(todo);
    }

    public PaginatedResponse<TodoResponse> getTodos(String email, boolean archived, int page, int size) {
        User user = userService.getUserByEmail(email);
        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.asc("completed"),
                Sort.Order.desc("createdAt")
            )
        );

        Page<Todo> todoPage = todoRepository.findByUserAndArchived(user, archived, pageable);
        return toPaginatedResponse(todoPage);
    }

    public PaginatedResponse<TodoResponse> searchTodos(String email, String query, boolean archived, int page, int size) {
        if (query == null || query.isBlank()) {
            return getTodos(email, archived, page, size);
        }

        User user = userService.getUserByEmail(email);
        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.asc("completed"),
                Sort.Order.desc("createdAt")
            )
        );

        Page<Todo> todoPage = todoRepository.searchByUserAndQuery(user, query, archived, pageable);
        return toPaginatedResponse(todoPage);
    }

    @Transactional
    public TodoResponse updateTodo(String email, UUID todoId, UpdateTodoRequest request) {
        User user = userService.getUserByEmail(email);
        Todo todo = todoRepository.findByIdAndUser(todoId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Todo not found with id: " + todoId));

        if (request.title() != null) {
            todo.setTitle(request.title().trim());
        }
        if (request.completed() != null) {
            todo.setCompleted(request.completed());
        }
        if (request.archived() != null) {
            todo.setArchived(request.archived());
        }

        Todo saved = todoRepository.save(todo);
        return toTodoResponse(saved);
    }

    @Transactional
    public void deleteTodo(String email, UUID todoId) {
        User user = userService.getUserByEmail(email);
        Todo todo = todoRepository.findByIdAndUser(todoId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Todo not found with id: " + todoId));

        todoRepository.delete(todo);
    }

    @Transactional
    public TodoResponse toggleComplete(String email, UUID todoId) {
        User user = userService.getUserByEmail(email);
        Todo todo = todoRepository.findByIdAndUser(todoId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Todo not found with id: " + todoId));

        todo.setCompleted(!todo.isCompleted());
        Todo saved = todoRepository.save(todo);
        return toTodoResponse(saved);
    }

    @Transactional
    public TodoResponse toggleArchive(String email, UUID todoId) {
        User user = userService.getUserByEmail(email);
        Todo todo = todoRepository.findByIdAndUser(todoId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Todo not found with id: " + todoId));

        todo.setArchived(!todo.isArchived());
        Todo saved = todoRepository.save(todo);
        return toTodoResponse(saved);
    }

    private TodoResponse toTodoResponse(Todo todo) {
        return new TodoResponse(
            todo.getId(),
            todo.getTitle(),
            todo.isCompleted(),
            todo.isArchived(),
            todo.getCreatedAt(),
            todo.getUpdatedAt()
        );
    }

    private PaginatedResponse<TodoResponse> toPaginatedResponse(Page<Todo> page) {
        List<TodoResponse> content = page.getContent().stream()
            .map(this::toTodoResponse)
            .toList();

        return new PaginatedResponse<>(
            content,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}