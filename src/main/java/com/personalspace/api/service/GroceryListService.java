package com.personalspace.api.service;

import com.personalspace.api.dto.request.CreateGroceryItemRequest;
import com.personalspace.api.dto.request.CreateGroceryListRequest;
import com.personalspace.api.dto.request.UpdateGroceryListRequest;
import com.personalspace.api.dto.response.GroceryItemLabelResponse;
import com.personalspace.api.dto.response.GroceryItemResponse;
import com.personalspace.api.dto.response.GroceryLabelResponse;
import com.personalspace.api.dto.response.GroceryListResponse;
import com.personalspace.api.dto.response.PaginatedResponse;
import com.personalspace.api.exception.ResourceNotFoundException;
import com.personalspace.api.model.entity.GroceryItem;
import com.personalspace.api.model.entity.GroceryItemLabel;
import com.personalspace.api.model.entity.GroceryLabel;
import com.personalspace.api.model.entity.GroceryList;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.repository.GroceryItemLabelRepository;
import com.personalspace.api.repository.GroceryItemRepository;
import com.personalspace.api.repository.GroceryLabelRepository;
import com.personalspace.api.repository.GroceryListRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroceryListService {

    private final GroceryListRepository groceryListRepository;
    private final GroceryItemRepository groceryItemRepository;
    private final GroceryLabelRepository groceryLabelRepository;
    private final GroceryItemLabelRepository groceryItemLabelRepository;
    private final UserService userService;

    public GroceryListService(GroceryListRepository groceryListRepository,
                              GroceryItemRepository groceryItemRepository,
                              GroceryLabelRepository groceryLabelRepository,
                              GroceryItemLabelRepository groceryItemLabelRepository,
                              UserService userService) {
        this.groceryListRepository = groceryListRepository;
        this.groceryItemRepository = groceryItemRepository;
        this.groceryLabelRepository = groceryLabelRepository;
        this.groceryItemLabelRepository = groceryItemLabelRepository;
        this.userService = userService;
    }

    @Transactional
    public GroceryListResponse createGroceryList(String email, CreateGroceryListRequest request) {
        User user = userService.getUserByEmail(email);

        GroceryList list = new GroceryList();
        list.setTitle(request.title());
        list.setUser(user);
        list.setLabels(resolveLabels(request.labelIds(), user));

        if (request.items() != null) {
            for (CreateGroceryItemRequest itemRequest : request.items()) {
                GroceryItem item = new GroceryItem();
                item.setName(itemRequest.name());
                item.setQuantity(itemRequest.quantity());
                item.setGroceryList(list);
                item.setLabels(resolveItemLabels(itemRequest.labelIds(), user));
                list.getItems().add(item);
            }
        }

        GroceryList saved = groceryListRepository.save(list);
        return toGroceryListResponse(saved);
    }

    public PaginatedResponse<GroceryListResponse> getGroceryLists(String email, boolean archived, int page, int size) {
        User user = userService.getUserByEmail(email);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
        Page<GroceryList> listPage = groceryListRepository.findByUserAndArchived(user, archived, pageable);

        List<GroceryListResponse> content = listPage.getContent().stream()
                .map(this::toGroceryListResponse)
                .toList();
        return new PaginatedResponse<>(
                content,
                listPage.getNumber(),
                listPage.getSize(),
                listPage.getTotalElements(),
                listPage.getTotalPages()
        );
    }

    public GroceryListResponse getGroceryList(String email, UUID listId) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));
        return toGroceryListResponse(list);
    }

    @Transactional
    public GroceryListResponse updateGroceryList(String email, UUID listId, UpdateGroceryListRequest request) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));

        list.setTitle(request.title());
        list.setLabels(resolveLabels(request.labelIds(), user));

        GroceryList saved = groceryListRepository.save(list);
        return toGroceryListResponse(saved);
    }

    @Transactional
    public void deleteGroceryList(String email, UUID listId) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));
        groceryListRepository.delete(list);
    }

    @Transactional
    public GroceryListResponse toggleArchive(String email, UUID listId) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));
        list.setArchived(!list.isArchived());
        GroceryList saved = groceryListRepository.save(list);
        return toGroceryListResponse(saved);
    }

    private Set<GroceryLabel> resolveLabels(List<UUID> labelIds, User user) {
        if (labelIds == null || labelIds.isEmpty()) {
            return new HashSet<>();
        }
        return labelIds.stream()
                .map(id -> groceryLabelRepository.findByIdAndUser(id, user)
                        .orElseThrow(() -> new ResourceNotFoundException("Label not found with id: " + id)))
                .collect(Collectors.toSet());
    }

    private Set<GroceryItemLabel> resolveItemLabels(List<UUID> labelIds, User user) {
        if (labelIds == null || labelIds.isEmpty()) {
            return new HashSet<>();
        }
        return labelIds.stream()
                .map(id -> groceryItemLabelRepository.findByIdAndUser(id, user)
                        .orElseThrow(() -> new ResourceNotFoundException("Label not found with id: " + id)))
                .collect(Collectors.toSet());
    }

    private GroceryListResponse toGroceryListResponse(GroceryList list) {
        List<GroceryLabelResponse> labelResponses = list.getLabels().stream()
                .map(label -> new GroceryLabelResponse(label.getId(), label.getName(), label.getCreatedAt()))
                .sorted(Comparator.comparing(GroceryLabelResponse::name))
                .toList();

        List<GroceryItem> sortedItems = groceryItemRepository
                .findByGroceryListOrderByCheckedAscCreatedAtAsc(list);

        List<GroceryItemResponse> itemResponses = sortedItems.stream()
                .map(this::toGroceryItemResponse)
                .toList();

        return new GroceryListResponse(
                list.getId(),
                list.getTitle(),
                list.isArchived(),
                labelResponses,
                itemResponses,
                list.getCreatedAt(),
                list.getUpdatedAt()
        );
    }

    private GroceryItemResponse toGroceryItemResponse(GroceryItem item) {
        List<GroceryItemLabelResponse> labelResponses = item.getLabels().stream()
                .map(label -> new GroceryItemLabelResponse(label.getId(), label.getName(), label.getCreatedAt()))
                .sorted(Comparator.comparing(GroceryItemLabelResponse::name))
                .toList();

        return new GroceryItemResponse(
                item.getId(),
                item.getName(),
                item.getQuantity(),
                item.isChecked(),
                labelResponses,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
