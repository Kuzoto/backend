package com.personalspace.api.service;

// AI-assisted test generated with ChatGPT.
// Prompt used: "Generate JUnit tests for TodoService following the style of NoteServiceTest in this repo."

import com.personalspace.api.dto.request.CreateTodoRequest;
import com.personalspace.api.dto.request.UpdateTodoRequest;
import com.personalspace.api.dto.response.PaginatedResponse;
import com.personalspace.api.dto.response.TodoResponse;
import com.personalspace.api.exception.ResourceNotFoundException;
import com.personalspace.api.model.entity.Todo;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private TodoService todoService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
    }

    @Test
    void createTodo_shouldReturnTodoResponse() {
        CreateTodoRequest request = new CreateTodoRequest("Test Todo");

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);

        Todo saved = createTestTodo(UUID.randomUUID(), "Test Todo", false, false, user);
        when(todoRepository.save(any(Todo.class))).thenReturn(saved);

        TodoResponse response = todoService.createTodo("test@test.com", request);

        assertNotNull(response);
        assertEquals("Test Todo", response.title());
        assertFalse(response.completed());
        assertFalse(response.archived());
    }

    @Test
    void getTodo_shouldReturnTodoResponse() {
        UUID todoId = UUID.randomUUID();
        Todo todo = createTestTodo(todoId, "Test Todo", false, false, user);

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.findByIdAndUser(todoId, user)).thenReturn(Optional.of(todo));

        TodoResponse response = todoService.getTodo("test@test.com", todoId);

        assertEquals("Test Todo", response.title());
        assertFalse(response.completed());
    }

    @Test
    void getTodo_shouldThrowWhenNotFound() {
        UUID todoId = UUID.randomUUID();

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.findByIdAndUser(todoId, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            todoService.getTodo("test@test.com", todoId)
        );
    }

    @Test
    void getTodos_shouldReturnPaginatedResponse() {
        Todo todo = createTestTodo(UUID.randomUUID(), "Test Todo", false, false, user);
        Page<Todo> page = new PageImpl<>(List.of(todo));

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.findByUserAndArchived(eq(user), eq(false), any(Pageable.class)))
            .thenReturn(page);

        PaginatedResponse<TodoResponse> response = todoService.getTodos("test@test.com", false, 0, 10);

        assertEquals(1, response.content().size());
        assertEquals(1, response.totalElements());
        assertEquals("Test Todo", response.content().get(0).title());
    }

    @Test
    void searchTodos_shouldSearchByQuery() {
        Todo todo = createTestTodo(UUID.randomUUID(), "Test Todo", false, false, user);
        Page<Todo> page = new PageImpl<>(List.of(todo));

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.searchByUserAndQuery(eq(user), eq("test"), eq(false), any(Pageable.class)))
            .thenReturn(page);

        PaginatedResponse<TodoResponse> response = todoService.searchTodos("test@test.com", "test", false, 0, 10);

        assertEquals(1, response.content().size());
        assertEquals("Test Todo", response.content().get(0).title());
    }

    @Test
    void searchTodos_shouldFallbackToGetTodosWhenQueryBlank() {
        Todo todo = createTestTodo(UUID.randomUUID(), "Test Todo", false, false, user);
        Page<Todo> page = new PageImpl<>(List.of(todo));

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.findByUserAndArchived(eq(user), eq(false), any(Pageable.class)))
            .thenReturn(page);

        PaginatedResponse<TodoResponse> response = todoService.searchTodos("test@test.com", "", false, 0, 10);

        assertEquals(1, response.content().size());
        verify(todoRepository, never()).searchByUserAndQuery(any(), any(), anyBoolean(), any(Pageable.class));
    }

    @Test
    void updateTodo_shouldUpdateFields() {
        UUID todoId = UUID.randomUUID();
        UpdateTodoRequest request = new UpdateTodoRequest("Updated Todo", true, true);

        Todo existing = createTestTodo(todoId, "Old Todo", false, false, user);
        Todo saved = createTestTodo(todoId, "Updated Todo", true, true, user);

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.findByIdAndUser(todoId, user)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenReturn(saved);

        TodoResponse response = todoService.updateTodo("test@test.com", todoId, request);

        assertEquals("Updated Todo", response.title());
        assertTrue(response.completed());
        assertTrue(response.archived());
    }

    @Test
    void deleteTodo_shouldDeleteTodo() {
        UUID todoId = UUID.randomUUID();
        Todo todo = createTestTodo(todoId, "Test Todo", false, false, user);

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.findByIdAndUser(todoId, user)).thenReturn(Optional.of(todo));

        todoService.deleteTodo("test@test.com", todoId);

        verify(todoRepository).delete(todo);
    }

    @Test
    void toggleComplete_shouldFlipCompletedStatus() {
        UUID todoId = UUID.randomUUID();
        Todo existing = createTestTodo(todoId, "Test Todo", false, false, user);
        Todo saved = createTestTodo(todoId, "Test Todo", true, false, user);

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.findByIdAndUser(todoId, user)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenReturn(saved);

        TodoResponse response = todoService.toggleComplete("test@test.com", todoId);

        assertTrue(response.completed());
        assertFalse(response.archived());
    }

    @Test
    void toggleArchive_shouldFlipArchivedStatus() {
        UUID todoId = UUID.randomUUID();
        Todo existing = createTestTodo(todoId, "Test Todo", false, false, user);
        Todo saved = createTestTodo(todoId, "Test Todo", false, true, user);

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(todoRepository.findByIdAndUser(todoId, user)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenReturn(saved);

        TodoResponse response = todoService.toggleArchive("test@test.com", todoId);

        assertTrue(response.archived());
        assertFalse(response.completed());
    }

    private Todo createTestTodo(UUID id, String title, boolean completed, boolean archived, User user) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setTitle(title);
        todo.setCompleted(completed);
        todo.setArchived(archived);
        todo.setUser(user);
        return todo;
    }
}