package com.personalspace.api.service;

import com.personalspace.api.dto.request.CreateGroceryItemRequest;
import com.personalspace.api.dto.request.UpdateGroceryItemRequest;
import com.personalspace.api.dto.response.GroceryItemLabelResponse;
import com.personalspace.api.dto.response.GroceryItemResponse;
import com.personalspace.api.exception.ResourceNotFoundException;
import com.personalspace.api.model.entity.GroceryItem;
import com.personalspace.api.model.entity.GroceryItemLabel;
import com.personalspace.api.model.entity.GroceryList;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.repository.GroceryItemLabelRepository;
import com.personalspace.api.repository.GroceryItemRepository;
import com.personalspace.api.repository.GroceryListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroceryItemService {

    private final GroceryItemRepository groceryItemRepository;
    private final GroceryListRepository groceryListRepository;
    private final GroceryItemLabelRepository groceryItemLabelRepository;
    private final UserService userService;

    public GroceryItemService(GroceryItemRepository groceryItemRepository,
                              GroceryListRepository groceryListRepository,
                              GroceryItemLabelRepository groceryItemLabelRepository,
                              UserService userService) {
        this.groceryItemRepository = groceryItemRepository;
        this.groceryListRepository = groceryListRepository;
        this.groceryItemLabelRepository = groceryItemLabelRepository;
        this.userService = userService;
    }

    @Transactional
    public GroceryItemResponse createItem(String email, UUID listId, CreateGroceryItemRequest request) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));

        GroceryItem item = new GroceryItem();
        item.setName(request.name());
        item.setQuantity(request.quantity());
        item.setGroceryList(list);
        item.setLabels(resolveLabels(request.labelIds(), user));

        GroceryItem saved = groceryItemRepository.save(item);
        return toGroceryItemResponse(saved);
    }

    public List<GroceryItemResponse> getItems(String email, UUID listId) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));

        return groceryItemRepository.findByGroceryListOrderByCheckedAscCreatedAtAsc(list).stream()
                .map(this::toGroceryItemResponse)
                .toList();
    }

    @Transactional
    public GroceryItemResponse updateItem(String email, UUID listId, UUID itemId, UpdateGroceryItemRequest request) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));

        GroceryItem item = groceryItemRepository.findByIdAndGroceryList(itemId, list)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery item not found with id: " + itemId));

        item.setName(request.name());
        item.setQuantity(request.quantity());
        item.setLabels(resolveLabels(request.labelIds(), user));

        GroceryItem saved = groceryItemRepository.save(item);
        return toGroceryItemResponse(saved);
    }

    @Transactional
    public void deleteItem(String email, UUID listId, UUID itemId) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));

        GroceryItem item = groceryItemRepository.findByIdAndGroceryList(itemId, list)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery item not found with id: " + itemId));

        groceryItemRepository.delete(item);
    }

    @Transactional
    public GroceryItemResponse toggleChecked(String email, UUID listId, UUID itemId) {
        User user = userService.getUserByEmail(email);
        GroceryList list = groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found with id: " + listId));

        GroceryItem item = groceryItemRepository.findByIdAndGroceryList(itemId, list)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery item not found with id: " + itemId));

        item.setChecked(!item.isChecked());
        GroceryItem saved = groceryItemRepository.save(item);

        return toGroceryItemResponse(saved);
    }

    private Set<GroceryItemLabel> resolveLabels(List<UUID> labelIds, User user) {
        if (labelIds == null || labelIds.isEmpty()) {
            return new HashSet<>();
        }
        return labelIds.stream()
                .map(id -> groceryItemLabelRepository.findByIdAndUser(id, user)
                        .orElseThrow(() -> new ResourceNotFoundException("Label not found with id: " + id)))
                .collect(Collectors.toSet());
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
