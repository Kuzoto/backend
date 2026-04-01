package com.personalspace.api.controller;

// AI-assisted test generated with ChatGPT.
// Prompt used: "Generate WebMvcTest tests for TodoController following the style of NoteControllerTest in this repo."

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalspace.api.dto.request.CreateTodoRequest;
import com.personalspace.api.dto.request.UpdateTodoRequest;
import com.personalspace.api.dto.response.PaginatedResponse;
import com.personalspace.api.dto.response.TodoResponse;
import com.personalspace.api.exception.ResourceNotFoundException;
import com.personalspace.api.service.TodoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = TodoController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.personalspace\\.api\\.security\\..*"
    )
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TodoService todoService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Principal mockPrincipal = () -> "test@test.com";

    private TodoResponse createTodoResponse() {
        return new TodoResponse(
            UUID.randomUUID(),
            "Test Todo",
            false,
            false,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    void createTodo_shouldReturn201() throws Exception {
        CreateTodoRequest request = new CreateTodoRequest("Test Todo");
        TodoResponse response = createTodoResponse();

        when(todoService.createTodo(anyString(), any(CreateTodoRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/todos")
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Test Todo"));
    }

    @Test
    void createTodo_shouldReturn400_whenValidationFails() throws Exception {
        CreateTodoRequest request = new CreateTodoRequest("");

        mockMvc.perform(post("/api/todos")
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getTodos_shouldReturn200() throws Exception {
        TodoResponse todoResponse = createTodoResponse();
        PaginatedResponse<TodoResponse> response = new PaginatedResponse<>(
            List.of(todoResponse), 0, 10, 1, 1
        );

        when(todoService.getTodos(anyString(), eq(false), eq(0), eq(10))).thenReturn(response);

        mockMvc.perform(get("/api/todos").principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("Test Todo"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getTodo_shouldReturn200() throws Exception {
        UUID todoId = UUID.randomUUID();
        TodoResponse response = createTodoResponse();

        when(todoService.getTodo(anyString(), eq(todoId))).thenReturn(response);

        mockMvc.perform(get("/api/todos/" + todoId).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Test Todo"));
    }

    @Test
    void getTodo_shouldReturn404_whenNotFound() throws Exception {
        UUID todoId = UUID.randomUUID();

        when(todoService.getTodo(anyString(), eq(todoId)))
            .thenThrow(new ResourceNotFoundException("Todo not found with id: " + todoId));

        mockMvc.perform(get("/api/todos/" + todoId).principal(mockPrincipal))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateTodo_shouldReturn200() throws Exception {
        UUID todoId = UUID.randomUUID();
        UpdateTodoRequest request = new UpdateTodoRequest("Updated Todo", true, true);
        TodoResponse response = new TodoResponse(
            todoId,
            "Updated Todo",
            true,
            true,
            Instant.now(),
            Instant.now()
        );

        when(todoService.updateTodo(anyString(), eq(todoId), any(UpdateTodoRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/todos/" + todoId)
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Todo"))
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void deleteTodo_shouldReturn204() throws Exception {
        UUID todoId = UUID.randomUUID();
        doNothing().when(todoService).deleteTodo(anyString(), eq(todoId));

        mockMvc.perform(delete("/api/todos/" + todoId).principal(mockPrincipal))
            .andExpect(status().isNoContent());
    }

    @Test
    void searchTodos_shouldReturn200() throws Exception {
        TodoResponse todoResponse = createTodoResponse();
        PaginatedResponse<TodoResponse> response = new PaginatedResponse<>(
            List.of(todoResponse), 0, 10, 1, 1
        );

        when(todoService.searchTodos(anyString(), eq("test"), eq(false), eq(0), eq(10))).thenReturn(response);

        mockMvc.perform(get("/api/todos/search")
                .param("q", "test")
                .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("Test Todo"));
    }

    @Test
    void toggleComplete_shouldReturn200() throws Exception {
        UUID todoId = UUID.randomUUID();
        TodoResponse response = new TodoResponse(
            todoId,
            "Test Todo",
            true,
            false,
            Instant.now(),
            Instant.now()
        );

        when(todoService.toggleComplete(anyString(), eq(todoId))).thenReturn(response);

        mockMvc.perform(patch("/api/todos/" + todoId + "/complete").principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void toggleArchive_shouldReturn200() throws Exception {
        UUID todoId = UUID.randomUUID();
        TodoResponse response = new TodoResponse(
            todoId,
            "Test Todo",
            false,
            true,
            Instant.now(),
            Instant.now()
        );

        when(todoService.toggleArchive(anyString(), eq(todoId))).thenReturn(response);

        mockMvc.perform(patch("/api/todos/" + todoId + "/archive").principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archived").value(true));
    }
}