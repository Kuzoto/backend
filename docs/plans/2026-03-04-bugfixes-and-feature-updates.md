# Bug Fixes & Feature Updates Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix auth premature-logout bug, remove grocery auto-archive, make titles optional, and split grocery item labels into their own independent pool.

**Architecture:** Four independent change sets applied in order: (1) enrich AuthResponse with `expiresAt`, (2) strip auto-archive logic, (3) relax title validation across DTOs and entities, (4) introduce a new `GroceryItemLabel` entity/service/controller and rewire `GroceryItem` to use it.

**Tech Stack:** Spring Boot 4.0.3, Spring Data JPA, Jakarta Validation, JUnit 5 + Mockito, H2 (tests), PostgreSQL (prod).

---

## Task 1: Add `expiresAt` to AuthResponse

**Files:**
- Modify: `src/main/java/com/personalspace/api/dto/response/AuthResponse.java`
- Modify: `src/main/java/com/personalspace/api/service/AuthService.java`
- Modify: `src/test/java/com/personalspace/api/controller/AuthControllerTest.java`
- Modify: `src/test/java/com/personalspace/api/service/AuthServiceTest.java` (if it constructs AuthResponse)

**Step 1: Update the failing test first**

In `AuthControllerTest.java`, the existing `AuthResponse` constructor call will break once we add `expiresAt`. Update every `new AuthResponse(...)` call in the test to include `expiresAt`. The compact constructor will change signature, so find and update all usages.

In `AuthControllerTest.java`, update the two places that construct `AuthResponse`:
```java
AuthResponse authResponse = new AuthResponse("access-token", "refresh-token", 900L, 9999999999000L, "Test", "test@test.com");
```

Also add an assertion that the response contains `expiresAt`:
```java
.andExpect(jsonPath("$.expiresAt").isNumber())
```

**Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=AuthControllerTest
```
Expected: FAIL — compilation error because `AuthResponse` compact constructor still has old signature.

**Step 3: Update AuthResponse**

Replace `src/main/java/com/personalspace/api/dto/response/AuthResponse.java`:
```java
package com.personalspace.api.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long expiresAt,
        String name,
        String email
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn, long expiresAt, String name, String email) {
        this(accessToken, refreshToken, "Bearer", expiresIn, expiresAt, name, email);
    }
}
```

**Step 4: Update AuthService — pass expiresAt everywhere AuthResponse is constructed**

In `AuthService.java`, all three methods (`signup`, `login`, `refresh`) build an `AuthResponse`. Add `expiresAt` computed as `System.currentTimeMillis() + jwtService.getAccessTokenExpiration()`.

Change every `new AuthResponse(accessToken, refreshToken, jwtService.getAccessTokenExpiration() / 1000, ...)` to:
```java
new AuthResponse(
    accessToken,
    refreshToken,
    jwtService.getAccessTokenExpiration() / 1000,
    System.currentTimeMillis() + jwtService.getAccessTokenExpiration(),
    user.getName(),
    user.getEmail()
)
```

Apply this in `signup()`, `login()`, and `refresh()`.

**Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=AuthControllerTest,AuthServiceTest
```
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/com/personalspace/api/dto/response/AuthResponse.java \
        src/main/java/com/personalspace/api/service/AuthService.java \
        src/test/java/com/personalspace/api/controller/AuthControllerTest.java
git commit -m "feat: add expiresAt to auth response to fix premature logout"
```

---

## Task 2: Remove Grocery Auto-Archive

**Files:**
- Modify: `src/main/java/com/personalspace/api/service/GroceryItemService.java`
- Modify: `src/main/java/com/personalspace/api/repository/GroceryItemRepository.java`
- Modify: `src/test/java/com/personalspace/api/service/GroceryItemServiceTest.java`

**Step 1: Update the tests first**

In `GroceryItemServiceTest.java`:

1. Delete the test `toggleChecked_shouldAutoArchiveWhenAllItemsChecked` (lines 165–182) entirely.
2. In `toggleChecked_shouldFlipCheckedStatus` (line 144), remove the mock setup line:
   ```java
   when(groceryItemRepository.existsByGroceryListAndCheckedFalse(groceryList)).thenReturn(true);
   ```
3. Delete the test `toggleChecked_shouldNotAutoUnarchiveOnUncheck` (lines 184–202) entirely — it tested that unchecking doesn't un-archive; irrelevant without auto-archive.
4. Add a new test asserting the list is never saved regardless of checked state:
   ```java
   @Test
   void toggleChecked_shouldNeverArchiveList() {
       UUID itemId = UUID.randomUUID();

       when(userService.getUserByEmail("test@test.com")).thenReturn(user);
       when(groceryListRepository.findByIdAndUser(listId, user)).thenReturn(Optional.of(groceryList));

       GroceryItem item = createTestGroceryItem(itemId, "Apples", null, false);
       when(groceryItemRepository.findByIdAndGroceryList(itemId, groceryList)).thenReturn(Optional.of(item));

       GroceryItem saved = createTestGroceryItem(itemId, "Apples", null, true);
       when(groceryItemRepository.save(any(GroceryItem.class))).thenReturn(saved);

       groceryItemService.toggleChecked("test@test.com", listId, itemId);

       verify(groceryListRepository, never()).save(any(GroceryList.class));
       assertFalse(groceryList.isArchived());
   }
   ```

**Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GroceryItemServiceTest
```
Expected: `toggleChecked_shouldNeverArchiveList` FAILS because auto-archive code still calls `groceryListRepository.save()`.

**Step 3: Remove auto-archive logic from GroceryItemService**

In `GroceryItemService.java`, in method `toggleChecked`, remove these lines (currently around 107–110):
```java
// Auto-archive: if item was just checked and no unchecked items remain
if (saved.isChecked() && !groceryItemRepository.existsByGroceryListAndCheckedFalse(list)) {
    list.setArchived(true);
    groceryListRepository.save(list);
}
```

**Step 4: Remove unused repository method**

In `GroceryItemRepository.java`, delete:
```java
boolean existsByGroceryListAndCheckedFalse(GroceryList list);
```

**Step 5: Run tests**

```bash
mvn test -Dtest=GroceryItemServiceTest
```
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/com/personalspace/api/service/GroceryItemService.java \
        src/main/java/com/personalspace/api/repository/GroceryItemRepository.java \
        src/test/java/com/personalspace/api/service/GroceryItemServiceTest.java
git commit -m "feat: remove grocery list auto-archive when all items checked"
```

---

## Task 3: Make Title Optional for Notes and Grocery Lists

**Files:**
- Modify: `src/main/java/com/personalspace/api/dto/request/CreateNoteRequest.java`
- Modify: `src/main/java/com/personalspace/api/dto/request/UpdateNoteRequest.java`
- Modify: `src/main/java/com/personalspace/api/dto/request/CreateGroceryListRequest.java`
- Modify: `src/main/java/com/personalspace/api/dto/request/UpdateGroceryListRequest.java`
- Modify: `src/main/java/com/personalspace/api/model/entity/Note.java`
- Modify: `src/main/java/com/personalspace/api/model/entity/GroceryList.java`
- Modify: `src/test/java/com/personalspace/api/controller/NoteControllerTest.java`
- Modify: `src/test/java/com/personalspace/api/controller/GroceryListControllerTest.java`

**Step 1: Update controller tests to assert null title is accepted**

In `NoteControllerTest.java`, add a test (or check if there's a test for blank title returning 400 — if so, delete or update it to expect 201/200 instead):
```java
@Test
void createNote_shouldReturn201_whenTitleIsNull() throws Exception {
    CreateNoteRequest request = new CreateNoteRequest(null, "Some content", false, List.of());
    NoteResponse response = new NoteResponse(UUID.randomUUID(), null, "Some content", false, false, List.of(), Instant.now(), Instant.now());

    when(noteService.createNote(anyString(), any(CreateNoteRequest.class))).thenReturn(response);

    mockMvc.perform(post("/api/notes")
                    .principal(mockPrincipal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
}
```

Do the same for `GroceryListControllerTest.java`:
```java
@Test
void createList_shouldReturn201_whenTitleIsNull() throws Exception {
    CreateGroceryListRequest request = new CreateGroceryListRequest(null, List.of(), List.of());
    // use whatever the existing createGroceryListResponse() helper produces, but with null title
    // mock service, assert 201
}
```

**Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=NoteControllerTest,GroceryListControllerTest
```
Expected: new null-title tests FAIL with 400 (validation still blocks null).

**Step 3: Update all four DTOs — remove @NotBlank**

`CreateNoteRequest.java` — remove `@NotBlank(message = "Title is required")`, keep `@Size`:
```java
public record CreateNoteRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,
        String content,
        Boolean pinned,
        List<UUID> labelIds
) {}
```

`UpdateNoteRequest.java` — same change:
```java
public record UpdateNoteRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,
        String content,
        Boolean pinned,
        Boolean archived,
        List<UUID> labelIds
) {}
```

`CreateGroceryListRequest.java`:
```java
public record CreateGroceryListRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,
        List<CreateGroceryItemRequest> items,
        List<UUID> labelIds
) {}
```

`UpdateGroceryListRequest.java`:
```java
public record UpdateGroceryListRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,
        List<UUID> labelIds
) {}
```

**Step 4: Update entities — allow null title**

`Note.java` — change title column annotation:
```java
// Before:
@Column(nullable = false)
private String title;

// After:
@Column
private String title;
```

`GroceryList.java` — same:
```java
// Before:
@Column(nullable = false)
private String title;

// After:
@Column
private String title;
```

**Step 5: Run tests**

```bash
mvn test -Dtest=NoteControllerTest,GroceryListControllerTest,NoteServiceTest,GroceryListServiceTest
```
Expected: PASS

**Step 6: Manually run DB migration on PostgreSQL**

> ⚠️ Hibernate's `ddl-auto=update` will NOT drop NOT NULL constraints from existing columns. Run these manually against your Postgres database before deploying:
> ```sql
> ALTER TABLE notes ALTER COLUMN title DROP NOT NULL;
> ALTER TABLE grocery_lists ALTER COLUMN title DROP NOT NULL;
> ```

**Step 7: Commit**

```bash
git add src/main/java/com/personalspace/api/dto/request/CreateNoteRequest.java \
        src/main/java/com/personalspace/api/dto/request/UpdateNoteRequest.java \
        src/main/java/com/personalspace/api/dto/request/CreateGroceryListRequest.java \
        src/main/java/com/personalspace/api/dto/request/UpdateGroceryListRequest.java \
        src/main/java/com/personalspace/api/model/entity/Note.java \
        src/main/java/com/personalspace/api/model/entity/GroceryList.java \
        src/test/java/com/personalspace/api/controller/NoteControllerTest.java \
        src/test/java/com/personalspace/api/controller/GroceryListControllerTest.java
git commit -m "feat: make title optional for notes and grocery lists"
```

---

## Task 4: New GroceryItemLabel — Entity, Repository, DTOs, Exception

**Files:**
- Create: `src/main/java/com/personalspace/api/model/entity/GroceryItemLabel.java`
- Create: `src/main/java/com/personalspace/api/repository/GroceryItemLabelRepository.java`
- Create: `src/main/java/com/personalspace/api/dto/request/CreateGroceryItemLabelRequest.java`
- Create: `src/main/java/com/personalspace/api/dto/request/UpdateGroceryItemLabelRequest.java`
- Create: `src/main/java/com/personalspace/api/dto/response/GroceryItemLabelResponse.java`
- Create: `src/main/java/com/personalspace/api/exception/DuplicateGroceryItemLabelException.java`
- Create: `src/test/java/com/personalspace/api/repository/GroceryItemLabelRepositoryTest.java`

**Step 1: Write failing repository test**

Create `src/test/java/com/personalspace/api/repository/GroceryItemLabelRepositoryTest.java`:
```java
package com.personalspace.api.repository;

import com.personalspace.api.model.entity.GroceryItemLabel;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class GroceryItemLabelRepositoryTest {

    @Autowired
    private GroceryItemLabelRepository groceryItemLabelRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setName("Test User");
        user.setEmail("test@test.com");
        user.setPassword("password");
        user.setRole(Role.USER);
        user = userRepository.save(user);
    }

    @Test
    void existsByNameAndUser_shouldReturnTrueWhenExists() {
        GroceryItemLabel label = new GroceryItemLabel();
        label.setName("Organic");
        label.setUser(user);
        groceryItemLabelRepository.save(label);

        assertTrue(groceryItemLabelRepository.existsByNameAndUser("Organic", user));
    }

    @Test
    void existsByNameAndUser_shouldReturnFalseWhenNotExists() {
        assertFalse(groceryItemLabelRepository.existsByNameAndUser("Missing", user));
    }

    @Test
    void findByIdAndUser_shouldReturnLabel() {
        GroceryItemLabel label = new GroceryItemLabel();
        label.setName("Frozen");
        label.setUser(user);
        GroceryItemLabel saved = groceryItemLabelRepository.save(label);

        Optional<GroceryItemLabel> found = groceryItemLabelRepository.findByIdAndUser(saved.getId(), user);
        assertTrue(found.isPresent());
        assertEquals("Frozen", found.get().getName());
    }

    @Test
    void findAllByUserOrderByNameAsc_shouldReturnSorted() {
        GroceryItemLabel b = new GroceryItemLabel(); b.setName("Bakery"); b.setUser(user);
        GroceryItemLabel a = new GroceryItemLabel(); a.setName("Aisle"); a.setUser(user);
        groceryItemLabelRepository.save(b);
        groceryItemLabelRepository.save(a);

        List<GroceryItemLabel> labels = groceryItemLabelRepository.findAllByUserOrderByNameAsc(user);
        assertEquals("Aisle", labels.get(0).getName());
        assertEquals("Bakery", labels.get(1).getName());
    }
}
```

**Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GroceryItemLabelRepositoryTest
```
Expected: FAIL — `GroceryItemLabel` and `GroceryItemLabelRepository` do not exist.

**Step 3: Create the entity**

Create `src/main/java/com/personalspace/api/model/entity/GroceryItemLabel.java`:
```java
package com.personalspace.api.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "grocery_item_labels", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "user_id"})
})
public class GroceryItemLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(mappedBy = "labels")
    private Set<GroceryItem> groceryItems = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public GroceryItemLabel() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Instant getCreatedAt() { return createdAt; }

    public Set<GroceryItem> getGroceryItems() { return groceryItems; }
    public void setGroceryItems(Set<GroceryItem> groceryItems) { this.groceryItems = groceryItems; }
}
```

**Step 4: Create the repository**

Create `src/main/java/com/personalspace/api/repository/GroceryItemLabelRepository.java`:
```java
package com.personalspace.api.repository;

import com.personalspace.api.model.entity.GroceryItemLabel;
import com.personalspace.api.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroceryItemLabelRepository extends JpaRepository<GroceryItemLabel, UUID> {
    boolean existsByNameAndUser(String name, User user);
    Optional<GroceryItemLabel> findByIdAndUser(UUID id, User user);
    List<GroceryItemLabel> findAllByUserOrderByNameAsc(User user);
}
```

**Step 5: Create the DTOs**

`src/main/java/com/personalspace/api/dto/request/CreateGroceryItemLabelRequest.java`:
```java
package com.personalspace.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroceryItemLabelRequest(
        @NotBlank(message = "Label name is required")
        @Size(max = 50, message = "Label name must not exceed 50 characters")
        String name
) {}
```

`src/main/java/com/personalspace/api/dto/request/UpdateGroceryItemLabelRequest.java`:
```java
package com.personalspace.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroceryItemLabelRequest(
        @NotBlank(message = "Label name is required")
        @Size(max = 50, message = "Label name must not exceed 50 characters")
        String name
) {}
```

`src/main/java/com/personalspace/api/dto/response/GroceryItemLabelResponse.java`:
```java
package com.personalspace.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record GroceryItemLabelResponse(UUID id, String name, Instant createdAt) {}
```

**Step 6: Create the exception**

`src/main/java/com/personalspace/api/exception/DuplicateGroceryItemLabelException.java`:
```java
package com.personalspace.api.exception;

public class DuplicateGroceryItemLabelException extends RuntimeException {
    public DuplicateGroceryItemLabelException(String message) {
        super(message);
    }
}
```

**Step 7: Run repository tests**

```bash
mvn test -Dtest=GroceryItemLabelRepositoryTest
```
Expected: PASS

**Step 8: Commit**

```bash
git add src/main/java/com/personalspace/api/model/entity/GroceryItemLabel.java \
        src/main/java/com/personalspace/api/repository/GroceryItemLabelRepository.java \
        src/main/java/com/personalspace/api/dto/request/CreateGroceryItemLabelRequest.java \
        src/main/java/com/personalspace/api/dto/request/UpdateGroceryItemLabelRequest.java \
        src/main/java/com/personalspace/api/dto/response/GroceryItemLabelResponse.java \
        src/main/java/com/personalspace/api/exception/DuplicateGroceryItemLabelException.java \
        src/test/java/com/personalspace/api/repository/GroceryItemLabelRepositoryTest.java
git commit -m "feat: add GroceryItemLabel entity, repository, DTOs, and exception"
```

---

## Task 5: GroceryItemLabelService + Test

**Files:**
- Create: `src/main/java/com/personalspace/api/service/GroceryItemLabelService.java`
- Create: `src/test/java/com/personalspace/api/service/GroceryItemLabelServiceTest.java`

**Step 1: Write failing service test**

Create `src/test/java/com/personalspace/api/service/GroceryItemLabelServiceTest.java`:
```java
package com.personalspace.api.service;

import com.personalspace.api.dto.request.CreateGroceryItemLabelRequest;
import com.personalspace.api.dto.request.UpdateGroceryItemLabelRequest;
import com.personalspace.api.dto.response.GroceryItemLabelResponse;
import com.personalspace.api.exception.DuplicateGroceryItemLabelException;
import com.personalspace.api.exception.ResourceNotFoundException;
import com.personalspace.api.model.entity.GroceryItem;
import com.personalspace.api.model.entity.GroceryItemLabel;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.repository.GroceryItemLabelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroceryItemLabelServiceTest {

    @Mock
    private GroceryItemLabelRepository groceryItemLabelRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private GroceryItemLabelService groceryItemLabelService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
    }

    @Test
    void createLabel_shouldReturnResponse() {
        CreateGroceryItemLabelRequest request = new CreateGroceryItemLabelRequest("Organic");

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(groceryItemLabelRepository.existsByNameAndUser("Organic", user)).thenReturn(false);

        GroceryItemLabel saved = makeLabel(UUID.randomUUID(), "Organic");
        when(groceryItemLabelRepository.save(any(GroceryItemLabel.class))).thenReturn(saved);

        GroceryItemLabelResponse response = groceryItemLabelService.createLabel("test@test.com", request);

        assertEquals("Organic", response.name());
    }

    @Test
    void createLabel_shouldThrowOnDuplicate() {
        CreateGroceryItemLabelRequest request = new CreateGroceryItemLabelRequest("Organic");

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(groceryItemLabelRepository.existsByNameAndUser("Organic", user)).thenReturn(true);

        assertThrows(DuplicateGroceryItemLabelException.class,
                () -> groceryItemLabelService.createLabel("test@test.com", request));
    }

    @Test
    void getLabels_shouldReturnList() {
        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(groceryItemLabelRepository.findAllByUserOrderByNameAsc(user))
                .thenReturn(List.of(makeLabel(UUID.randomUUID(), "Frozen")));

        List<GroceryItemLabelResponse> labels = groceryItemLabelService.getLabels("test@test.com");

        assertEquals(1, labels.size());
        assertEquals("Frozen", labels.get(0).name());
    }

    @Test
    void updateLabel_shouldUpdateName() {
        UUID labelId = UUID.randomUUID();
        UpdateGroceryItemLabelRequest request = new UpdateGroceryItemLabelRequest("Updated");

        GroceryItemLabel existing = makeLabel(labelId, "Organic");
        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(groceryItemLabelRepository.findByIdAndUser(labelId, user)).thenReturn(Optional.of(existing));
        when(groceryItemLabelRepository.existsByNameAndUser("Updated", user)).thenReturn(false);

        GroceryItemLabel saved = makeLabel(labelId, "Updated");
        when(groceryItemLabelRepository.save(any(GroceryItemLabel.class))).thenReturn(saved);

        GroceryItemLabelResponse response = groceryItemLabelService.updateLabel("test@test.com", labelId, request);
        assertEquals("Updated", response.name());
    }

    @Test
    void updateLabel_shouldThrowOnDuplicate() {
        UUID labelId = UUID.randomUUID();
        UpdateGroceryItemLabelRequest request = new UpdateGroceryItemLabelRequest("Conflict");

        GroceryItemLabel existing = makeLabel(labelId, "Organic");
        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(groceryItemLabelRepository.findByIdAndUser(labelId, user)).thenReturn(Optional.of(existing));
        when(groceryItemLabelRepository.existsByNameAndUser("Conflict", user)).thenReturn(true);

        assertThrows(DuplicateGroceryItemLabelException.class,
                () -> groceryItemLabelService.updateLabel("test@test.com", labelId, request));
    }

    @Test
    void deleteLabel_shouldDeleteAndCleanUpItems() {
        UUID labelId = UUID.randomUUID();
        GroceryItemLabel label = makeLabel(labelId, "Bulk");
        GroceryItem item = new GroceryItem();
        item.setLabels(new HashSet<>(Set.of(label)));
        label.setGroceryItems(new HashSet<>(Set.of(item)));

        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(groceryItemLabelRepository.findByIdAndUser(labelId, user)).thenReturn(Optional.of(label));

        groceryItemLabelService.deleteLabel("test@test.com", labelId);

        verify(groceryItemLabelRepository).delete(label);
        assertFalse(item.getLabels().contains(label));
    }

    @Test
    void deleteLabel_shouldThrowWhenNotFound() {
        UUID labelId = UUID.randomUUID();
        when(userService.getUserByEmail("test@test.com")).thenReturn(user);
        when(groceryItemLabelRepository.findByIdAndUser(labelId, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> groceryItemLabelService.deleteLabel("test@test.com", labelId));
    }

    private GroceryItemLabel makeLabel(UUID id, String name) {
        GroceryItemLabel label = new GroceryItemLabel();
        label.setId(id);
        label.setName(name);
        label.setUser(user);
        label.setGroceryItems(new HashSet<>());
        return label;
    }
}
```

**Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GroceryItemLabelServiceTest
```
Expected: FAIL — `GroceryItemLabelService` does not exist.

**Step 3: Create the service**

Create `src/main/java/com/personalspace/api/service/GroceryItemLabelService.java`:
```java
package com.personalspace.api.service;

import com.personalspace.api.dto.request.CreateGroceryItemLabelRequest;
import com.personalspace.api.dto.request.UpdateGroceryItemLabelRequest;
import com.personalspace.api.dto.response.GroceryItemLabelResponse;
import com.personalspace.api.exception.DuplicateGroceryItemLabelException;
import com.personalspace.api.exception.ResourceNotFoundException;
import com.personalspace.api.model.entity.GroceryItemLabel;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.repository.GroceryItemLabelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GroceryItemLabelService {

    private final GroceryItemLabelRepository groceryItemLabelRepository;
    private final UserService userService;

    public GroceryItemLabelService(GroceryItemLabelRepository groceryItemLabelRepository, UserService userService) {
        this.groceryItemLabelRepository = groceryItemLabelRepository;
        this.userService = userService;
    }

    @Transactional
    public GroceryItemLabelResponse createLabel(String email, CreateGroceryItemLabelRequest request) {
        User user = userService.getUserByEmail(email);

        if (groceryItemLabelRepository.existsByNameAndUser(request.name(), user)) {
            throw new DuplicateGroceryItemLabelException("Label already exists: " + request.name());
        }

        GroceryItemLabel label = new GroceryItemLabel();
        label.setName(request.name());
        label.setUser(user);

        GroceryItemLabel saved = groceryItemLabelRepository.save(label);
        return toResponse(saved);
    }

    public List<GroceryItemLabelResponse> getLabels(String email) {
        User user = userService.getUserByEmail(email);
        return groceryItemLabelRepository.findAllByUserOrderByNameAsc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public GroceryItemLabelResponse updateLabel(String email, UUID labelId, UpdateGroceryItemLabelRequest request) {
        User user = userService.getUserByEmail(email);
        GroceryItemLabel label = groceryItemLabelRepository.findByIdAndUser(labelId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found with id: " + labelId));

        if (!label.getName().equals(request.name()) && groceryItemLabelRepository.existsByNameAndUser(request.name(), user)) {
            throw new DuplicateGroceryItemLabelException("Label already exists: " + request.name());
        }

        label.setName(request.name());
        GroceryItemLabel saved = groceryItemLabelRepository.save(label);
        return toResponse(saved);
    }

    @Transactional
    public void deleteLabel(String email, UUID labelId) {
        User user = userService.getUserByEmail(email);
        GroceryItemLabel label = groceryItemLabelRepository.findByIdAndUser(labelId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found with id: " + labelId));

        label.getGroceryItems().forEach(item -> item.getLabels().remove(label));

        groceryItemLabelRepository.delete(label);
    }

    private GroceryItemLabelResponse toResponse(GroceryItemLabel label) {
        return new GroceryItemLabelResponse(label.getId(), label.getName(), label.getCreatedAt());
    }
}
```

**Step 4: Run tests**

```bash
mvn test -Dtest=GroceryItemLabelServiceTest
```
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/personalspace/api/service/GroceryItemLabelService.java \
        src/test/java/com/personalspace/api/service/GroceryItemLabelServiceTest.java
git commit -m "feat: add GroceryItemLabelService with CRUD"
```

---

## Task 6: GroceryItemLabelController + Test + GlobalExceptionHandler

**Files:**
- Create: `src/main/java/com/personalspace/api/controller/GroceryItemLabelController.java`
- Create: `src/test/java/com/personalspace/api/controller/GroceryItemLabelControllerTest.java`
- Modify: `src/main/java/com/personalspace/api/exception/GlobalExceptionHandler.java`

**Step 1: Write failing controller test**

Create `src/test/java/com/personalspace/api/controller/GroceryItemLabelControllerTest.java`:
```java
package com.personalspace.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalspace.api.dto.request.CreateGroceryItemLabelRequest;
import com.personalspace.api.dto.request.UpdateGroceryItemLabelRequest;
import com.personalspace.api.dto.response.GroceryItemLabelResponse;
import com.personalspace.api.exception.DuplicateGroceryItemLabelException;
import com.personalspace.api.service.GroceryItemLabelService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = GroceryItemLabelController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.personalspace\\.api\\.security\\..*")
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class GroceryItemLabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroceryItemLabelService groceryItemLabelService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Principal mockPrincipal = () -> "test@test.com";

    @Test
    void createLabel_shouldReturn201() throws Exception {
        CreateGroceryItemLabelRequest request = new CreateGroceryItemLabelRequest("Organic");
        GroceryItemLabelResponse response = new GroceryItemLabelResponse(UUID.randomUUID(), "Organic", Instant.now());

        when(groceryItemLabelService.createLabel(anyString(), any(CreateGroceryItemLabelRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/grocery-item-labels")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Organic"));
    }

    @Test
    void createLabel_shouldReturn400_whenBlank() throws Exception {
        CreateGroceryItemLabelRequest request = new CreateGroceryItemLabelRequest("");

        mockMvc.perform(post("/api/grocery-item-labels")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLabel_shouldReturn409_whenDuplicate() throws Exception {
        CreateGroceryItemLabelRequest request = new CreateGroceryItemLabelRequest("Organic");

        when(groceryItemLabelService.createLabel(anyString(), any(CreateGroceryItemLabelRequest.class)))
                .thenThrow(new DuplicateGroceryItemLabelException("Label already exists: Organic"));

        mockMvc.perform(post("/api/grocery-item-labels")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void getLabels_shouldReturn200() throws Exception {
        GroceryItemLabelResponse label = new GroceryItemLabelResponse(UUID.randomUUID(), "Organic", Instant.now());

        when(groceryItemLabelService.getLabels(anyString())).thenReturn(List.of(label));

        mockMvc.perform(get("/api/grocery-item-labels").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Organic"));
    }

    @Test
    void updateLabel_shouldReturn200() throws Exception {
        UUID labelId = UUID.randomUUID();
        UpdateGroceryItemLabelRequest request = new UpdateGroceryItemLabelRequest("Updated");
        GroceryItemLabelResponse response = new GroceryItemLabelResponse(labelId, "Updated", Instant.now());

        when(groceryItemLabelService.updateLabel(anyString(), eq(labelId), any(UpdateGroceryItemLabelRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/grocery-item-labels/" + labelId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void deleteLabel_shouldReturn204() throws Exception {
        UUID labelId = UUID.randomUUID();

        doNothing().when(groceryItemLabelService).deleteLabel(anyString(), eq(labelId));

        mockMvc.perform(delete("/api/grocery-item-labels/" + labelId).principal(mockPrincipal))
                .andExpect(status().isNoContent());
    }
}
```

**Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=GroceryItemLabelControllerTest
```
Expected: FAIL — controller does not exist.

**Step 3: Add DuplicateGroceryItemLabelException handler to GlobalExceptionHandler**

In `GlobalExceptionHandler.java`, add after the `handleDuplicateGroceryLabel` method:
```java
@ExceptionHandler(DuplicateGroceryItemLabelException.class)
public ResponseEntity<ApiErrorResponse> handleDuplicateGroceryItemLabel(DuplicateGroceryItemLabelException ex) {
    ApiErrorResponse response = new ApiErrorResponse(
            HttpStatus.CONFLICT.value(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
}
```

**Step 4: Create the controller**

Create `src/main/java/com/personalspace/api/controller/GroceryItemLabelController.java`:
```java
package com.personalspace.api.controller;

import com.personalspace.api.dto.request.CreateGroceryItemLabelRequest;
import com.personalspace.api.dto.request.UpdateGroceryItemLabelRequest;
import com.personalspace.api.dto.response.GroceryItemLabelResponse;
import com.personalspace.api.service.GroceryItemLabelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/grocery-item-labels")
public class GroceryItemLabelController {

    private final GroceryItemLabelService groceryItemLabelService;

    public GroceryItemLabelController(GroceryItemLabelService groceryItemLabelService) {
        this.groceryItemLabelService = groceryItemLabelService;
    }

    @PostMapping
    public ResponseEntity<GroceryItemLabelResponse> createLabel(
            Principal principal,
            @Valid @RequestBody CreateGroceryItemLabelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groceryItemLabelService.createLabel(principal.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<GroceryItemLabelResponse>> getLabels(Principal principal) {
        return ResponseEntity.ok(groceryItemLabelService.getLabels(principal.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroceryItemLabelResponse> updateLabel(
            Principal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGroceryItemLabelRequest request) {
        return ResponseEntity.ok(groceryItemLabelService.updateLabel(principal.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLabel(Principal principal, @PathVariable UUID id) {
        groceryItemLabelService.deleteLabel(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
```

**Step 5: Run tests**

```bash
mvn test -Dtest=GroceryItemLabelControllerTest
```
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/com/personalspace/api/controller/GroceryItemLabelController.java \
        src/main/java/com/personalspace/api/exception/GlobalExceptionHandler.java \
        src/test/java/com/personalspace/api/controller/GroceryItemLabelControllerTest.java
git commit -m "feat: add GroceryItemLabelController at /api/grocery-item-labels"
```

---

## Task 7: Rewire GroceryItem to Use GroceryItemLabel

**Files:**
- Modify: `src/main/java/com/personalspace/api/model/entity/GroceryItem.java`
- Modify: `src/main/java/com/personalspace/api/dto/response/GroceryItemResponse.java`
- Modify: `src/main/java/com/personalspace/api/service/GroceryItemService.java`
- Modify: `src/test/java/com/personalspace/api/service/GroceryItemServiceTest.java`
- Modify: `src/test/java/com/personalspace/api/controller/GroceryItemControllerTest.java`

**Step 1: Update GroceryItem entity**

In `GroceryItem.java`:

1. Change import from `GroceryLabel` to `GroceryItemLabel`.
2. Change the ManyToMany field and join table:
```java
@ManyToMany
@JoinTable(
        name = "grocery_item_label_mappings",
        joinColumns = @JoinColumn(name = "grocery_item_id"),
        inverseJoinColumns = @JoinColumn(name = "grocery_item_label_id")
)
private Set<GroceryItemLabel> labels = new HashSet<>();
```
3. Update getter/setter types: `Set<GroceryItemLabel>`.

**Step 2: Update GroceryItemResponse**

In `GroceryItemResponse.java`, change:
```java
// Before:
List<GroceryLabelResponse> labels,

// After:
List<GroceryItemLabelResponse> labels,
```

**Step 3: Update GroceryItemService**

In `GroceryItemService.java`:

1. Replace `GroceryLabelRepository` dependency with `GroceryItemLabelRepository`.
2. Update constructor parameter accordingly.
3. In `resolveLabels`, change `GroceryLabel` → `GroceryItemLabel` and use the new repository.
4. In `toGroceryItemResponse`, change label mapping to produce `GroceryItemLabelResponse`:
```java
List<GroceryItemLabelResponse> labelResponses = item.getLabels().stream()
        .map(label -> new GroceryItemLabelResponse(label.getId(), label.getName(), label.getCreatedAt()))
        .sorted(Comparator.comparing(GroceryItemLabelResponse::name))
        .toList();
```

**Step 4: Update GroceryItemServiceTest**

In `GroceryItemServiceTest.java`:

1. Replace `@Mock GroceryLabelRepository groceryLabelRepository` with `@Mock GroceryItemLabelRepository groceryItemLabelRepository`.
2. Update imports accordingly.
3. The `createTestGroceryItem` helper calls `item.setLabels(new HashSet<>())` — this still works since `GroceryItem.labels` is now `Set<GroceryItemLabel>`, just ensure there's no type mismatch.

**Step 5: Run tests**

```bash
mvn test -Dtest=GroceryItemServiceTest,GroceryItemControllerTest
```
Expected: PASS (GroceryItemControllerTest may need minor adjustments if it references `GroceryLabelResponse` — update those to `GroceryItemLabelResponse`).

**Step 6: Manual DB migration**

> ⚠️ Run on PostgreSQL before deploying:
> ```sql
> -- Create new item label table
> CREATE TABLE grocery_item_labels (
>   id UUID PRIMARY KEY,
>   name VARCHAR(50) NOT NULL,
>   user_id UUID NOT NULL REFERENCES users(id),
>   created_at TIMESTAMP NOT NULL,
>   UNIQUE (name, user_id)
> );
>
> -- Drop old FK and rename column in join table
> ALTER TABLE grocery_item_label_mappings
>   DROP CONSTRAINT IF EXISTS fk_grocery_item_label_mappings_grocery_label;
>
> ALTER TABLE grocery_item_label_mappings
>   RENAME COLUMN grocery_label_id TO grocery_item_label_id;
>
> ALTER TABLE grocery_item_label_mappings
>   ADD CONSTRAINT fk_grocery_item_label_mappings_grocery_item_label
>   FOREIGN KEY (grocery_item_label_id) REFERENCES grocery_item_labels(id);
> ```

**Step 7: Commit**

```bash
git add src/main/java/com/personalspace/api/model/entity/GroceryItem.java \
        src/main/java/com/personalspace/api/dto/response/GroceryItemResponse.java \
        src/main/java/com/personalspace/api/service/GroceryItemService.java \
        src/test/java/com/personalspace/api/service/GroceryItemServiceTest.java \
        src/test/java/com/personalspace/api/controller/GroceryItemControllerTest.java
git commit -m "feat: rewire GroceryItem labels to use GroceryItemLabel"
```

---

## Task 8: Clean Up GroceryLabel — Remove Item Relationship

**Files:**
- Modify: `src/main/java/com/personalspace/api/model/entity/GroceryLabel.java`
- Modify: `src/main/java/com/personalspace/api/service/GroceryLabelService.java`
- Modify: `src/test/java/com/personalspace/api/service/GroceryLabelServiceTest.java`

**Step 1: Update GroceryLabelService test — remove item cleanup assertion**

In `GroceryLabelServiceTest.java`, find any test that asserts `deleteLabel` removes the label from grocery items. Remove that assertion (or update the test to only verify list cleanup and label deletion).

**Step 2: Run test to see it fails**

```bash
mvn test -Dtest=GroceryLabelServiceTest
```
Expected: The item-cleanup test FAILS because GroceryLabel still has `groceryItems` (compile will succeed but logic test may fail depending on test setup).

**Step 3: Remove groceryItems from GroceryLabel entity**

In `GroceryLabel.java`:
1. Remove the `@ManyToMany(mappedBy = "labels")` field for `groceryItems`.
2. Remove the getter/setter for `groceryItems`.

**Step 4: Remove item cleanup from GroceryLabelService.deleteLabel**

In `GroceryLabelService.java`, in `deleteLabel`, remove:
```java
label.getGroceryItems().forEach(item -> item.getLabels().remove(label));
```

**Step 5: Run all tests**

```bash
mvn clean test
```
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/com/personalspace/api/model/entity/GroceryLabel.java \
        src/main/java/com/personalspace/api/service/GroceryLabelService.java \
        src/test/java/com/personalspace/api/service/GroceryLabelServiceTest.java
git commit -m "refactor: remove groceryItems relationship from GroceryLabel"
```

---

## Final Verification

Run the full test suite:
```bash
mvn clean test
```
Expected: All tests pass, zero failures.

Then do a final commit if needed, and update `CLAUDE.md` or docs to reflect the new `/api/grocery-item-labels` endpoints.
