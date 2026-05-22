package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.ContactRequest;
import com.example.tool.dto.ContactResponse;
import com.example.tool.entity.ContactEntity;
import com.example.tool.mapper.ContactMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private static final String DEFAULT_STATUS = "active";
    private static final String ALL_STATUS = "all";

    private final ContactMapper contactMapper;

    public ContactService(ContactMapper contactMapper) {
        this.contactMapper = contactMapper;
    }

    public List<ContactResponse> list(Long userId, String status, String keyword) {
        LambdaQueryWrapper<ContactEntity> wrapper = new LambdaQueryWrapper<ContactEntity>()
            .eq(userId != null, ContactEntity::getUserId, userId)
            .eq(shouldFilterByStatus(status), ContactEntity::getStatus, status)
            .and(StringUtils.hasText(keyword), query -> query
                .like(ContactEntity::getName, keyword)
                .or()
                .like(ContactEntity::getPhone, keyword)
                .or()
                .like(ContactEntity::getRemark, keyword))
            .orderByAsc(ContactEntity::getSortOrder)
            .orderByAsc(ContactEntity::getId);

        return contactMapper.selectList(wrapper).stream()
            .map(this::toResponse)
            .toList();
    }

    private boolean shouldFilterByStatus(String status) {
        return StringUtils.hasText(status) && !ALL_STATUS.equalsIgnoreCase(status.trim());
    }

    public Optional<ContactResponse> getById(Long id) {
        return Optional.ofNullable(contactMapper.selectById(id)).map(this::toResponse);
    }

    public ContactResponse create(ContactRequest request) {
        validatePhoneUnique(request.getUserId(), request.getPhone(), null);

        ContactEntity entity = new ContactEntity();
        fillEntity(entity, request);
        contactMapper.insert(entity);

        return toResponse(contactMapper.selectById(entity.getId()));
    }

    public Optional<ContactResponse> update(Long id, ContactRequest request) {
        ContactEntity entity = contactMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        validatePhoneUnique(request.getUserId(), request.getPhone(), id);
        fillEntity(entity, request);
        contactMapper.updateById(entity);

        return Optional.of(toResponse(contactMapper.selectById(id)));
    }

    public boolean delete(Long id) {
        return contactMapper.deleteById(id) > 0;
    }

    private void validatePhoneUnique(Long userId, String phone, Long ignoredId) {
        if (!StringUtils.hasText(phone)) {
            return;
        }

        LambdaQueryWrapper<ContactEntity> wrapper = new LambdaQueryWrapper<ContactEntity>()
            .eq(ContactEntity::getUserId, userId)
            .eq(ContactEntity::getPhone, phone.trim())
            .ne(ignoredId != null, ContactEntity::getId, ignoredId)
            .last("LIMIT 1");

        if (contactMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("联系人手机号已存在");
        }
    }

    private void fillEntity(ContactEntity entity, ContactRequest request) {
        entity.setUserId(request.getUserId());
        entity.setName(request.getName().trim());
        entity.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
        entity.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
    }

    private ContactResponse toResponse(ContactEntity entity) {
        ContactResponse response = new ContactResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setName(entity.getName());
        response.setPhone(entity.getPhone());
        response.setRemark(entity.getRemark());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
