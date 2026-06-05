package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.auth.dto.FamilyMemberResponse;
import com.example.auth.dto.FamilyOverviewResponse;
import com.example.auth.dto.JoinFamilyRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.entity.FamilyEntity;
import com.example.auth.entity.UserEntity;
import com.example.auth.mapper.FamilyMapper;
import com.example.auth.mapper.UserMapper;
import com.example.auth.model.UserAccount;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private static final String ACTIVE_STATUS = "active";
    private static final String ARCHIVED_STATUS = "archived";
    private static final String DISABLED_STATUS = "disabled";
    private static final String INVITE_CODE_PREFIX = "FAMILY-";
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final FamilyMapper familyMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(PasswordEncoder passwordEncoder, UserMapper userMapper, FamilyMapper familyMapper) {
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.familyMapper = familyMapper;
    }

    public Optional<UserAccount> authenticate(String login, String password) {
        return findActiveByLogin(login)
            .filter(user -> user.passwordHash() != null)
            .filter(user -> passwordEncoder.matches(password, user.passwordHash()));
    }

    public Optional<UserAccount> getCurrentUser(String username) {
        return findActiveByLogin(username);
    }

    @Transactional
    public void register(RegisterRequest request) {
        String normalizedUsername = request.username().trim();
        if (existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        UserEntity entity = new UserEntity();
        entity.setUsername(normalizedUsername);
        entity.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        entity.setDisplayName(normalizedUsername);
        entity.setStatus(ACTIVE_STATUS);
        entity.setRoleName("user");
        userMapper.insert(entity);
    }

    public void markLoginSuccess(Long userId) {
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .set(UserEntity::getLastLoginAt, LocalDateTime.now());
        userMapper.update(null, wrapper);
    }

    @Transactional(readOnly = true)
    public FamilyOverviewResponse getFamilyOverview(String username) {
        UserEntity currentUser = requireActiveUserEntity(username);
        if (currentUser.getFamilyId() == null) {
            return new FamilyOverviewResponse(false, null, 0, List.of());
        }

        FamilyEntity family = requireActiveFamily(currentUser.getFamilyId());
        return buildFamilyOverview(currentUser, family);
    }

    @Transactional
    public FamilyOverviewResponse createFamily(String username) {
        UserEntity currentUser = requireActiveUserEntity(username);
        if (currentUser.getFamilyId() != null) {
            throw new IllegalArgumentException("你已加入家庭，不能再创建新家庭");
        }

        FamilyEntity ownedFamily = findOwnedActiveFamily(currentUser.getId()).orElse(null);
        if (ownedFamily == null) {
            ownedFamily = new FamilyEntity();
            ownedFamily.setOwnerUserId(currentUser.getId());
            ownedFamily.setInviteCode(generateInviteCode());
            ownedFamily.setFamilyName(currentUser.getDisplayName() + "的家庭");
            ownedFamily.setStatus(ACTIVE_STATUS);
            familyMapper.insert(ownedFamily);
        }

        updateUserFamilyId(currentUser.getId(), ownedFamily.getId());
        currentUser.setFamilyId(ownedFamily.getId());
        return buildFamilyOverview(currentUser, ownedFamily);
    }

    @Transactional
    public FamilyOverviewResponse joinFamily(String username, JoinFamilyRequest request) {
        UserEntity currentUser = requireActiveUserEntity(username);
        if (currentUser.getFamilyId() != null) {
            throw new IllegalArgumentException("你已加入家庭，不能再加入其他家庭");
        }

        String normalizedInviteCode = normalizeInviteCode(request.inviteCode());
        FamilyEntity family = findActiveFamilyByInviteCode(normalizedInviteCode)
            .orElseThrow(() -> new IllegalArgumentException("家庭邀请码不存在"));

        updateUserFamilyId(currentUser.getId(), family.getId());
        currentUser.setFamilyId(family.getId());
        return buildFamilyOverview(currentUser, family);
    }

    @Transactional
    public FamilyOverviewResponse unbindFamilyMember(String username, Long memberUserId) {
        UserEntity currentUser = requireActiveUserEntity(username);
        if (currentUser.getFamilyId() == null) {
            throw new IllegalArgumentException("你当前还未加入家庭");
        }

        FamilyEntity family = requireActiveFamily(currentUser.getFamilyId());
        if (!currentUser.getId().equals(family.getOwnerUserId())) {
            throw new IllegalArgumentException("只有家庭管理员可以解绑成员");
        }
        if (currentUser.getId().equals(memberUserId)) {
            throw new IllegalArgumentException("不能解绑自己");
        }

        UserEntity targetUser = requireActiveUserById(memberUserId);
        if (!family.getId().equals(targetUser.getFamilyId())) {
            throw new IllegalArgumentException("该用户不在当前家庭中");
        }

        updateUserFamilyId(targetUser.getId(), null);
        return buildFamilyOverview(currentUser, family);
    }

    @Transactional
    public void deleteAccount(String username) {
        UserEntity currentUser = requireActiveUserEntity(username);
        Long familyId = currentUser.getFamilyId();

        if (familyId != null) {
            FamilyEntity family = requireActiveFamily(familyId);
            if (currentUser.getId().equals(family.getOwnerUserId())) {
                handleOwnerAccountDeletion(currentUser, family);
            } else {
                updateUserFamilyId(currentUser.getId(), null);
            }
        }

        disableUserAccount(currentUser.getId());
    }

    private Optional<UserAccount> findActiveByLogin(String login) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getStatus, ACTIVE_STATUS)
            .and(query -> query
                .eq(UserEntity::getUsername, login)
                .or()
                .eq(UserEntity::getPhone, login)
                .or()
                .eq(UserEntity::getEmail, login)
            )
            .last("LIMIT 1");

        return Optional.ofNullable(userMapper.selectOne(wrapper)).map(this::toUserAccount);
    }

    private boolean existsByUsername(String username) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getUsername, username)
            .last("LIMIT 1");
        return userMapper.selectOne(wrapper) != null;
    }

    private UserEntity requireActiveUserEntity(String username) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getUsername, username)
            .eq(UserEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1");
        UserEntity entity = userMapper.selectOne(wrapper);
        if (entity == null) {
            throw new IllegalArgumentException("当前用户不存在或已失效");
        }
        return entity;
    }

    private UserEntity requireActiveUserById(Long userId) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .eq(UserEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1");
        UserEntity entity = userMapper.selectOne(wrapper);
        if (entity == null) {
            throw new IllegalArgumentException("家庭成员不存在");
        }
        return entity;
    }

    private Optional<FamilyEntity> findOwnedActiveFamily(Long ownerUserId) {
        LambdaQueryWrapper<FamilyEntity> wrapper = new LambdaQueryWrapper<FamilyEntity>()
            .eq(FamilyEntity::getOwnerUserId, ownerUserId)
            .eq(FamilyEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1");
        return Optional.ofNullable(familyMapper.selectOne(wrapper));
    }

    private Optional<FamilyEntity> findActiveFamilyByInviteCode(String inviteCode) {
        LambdaQueryWrapper<FamilyEntity> wrapper = new LambdaQueryWrapper<FamilyEntity>()
            .eq(FamilyEntity::getInviteCode, inviteCode)
            .eq(FamilyEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1");
        return Optional.ofNullable(familyMapper.selectOne(wrapper));
    }

    private FamilyEntity requireActiveFamily(Long familyId) {
        LambdaQueryWrapper<FamilyEntity> wrapper = new LambdaQueryWrapper<FamilyEntity>()
            .eq(FamilyEntity::getId, familyId)
            .eq(FamilyEntity::getStatus, ACTIVE_STATUS)
            .last("LIMIT 1");
        FamilyEntity entity = familyMapper.selectOne(wrapper);
        if (entity == null) {
            throw new IllegalArgumentException("家庭不存在或已失效");
        }
        return entity;
    }

    private void handleOwnerAccountDeletion(UserEntity currentUser, FamilyEntity family) {
        List<UserEntity> activeMembers = listActiveFamilyUsers(family.getId()).stream()
            .filter(user -> !user.getId().equals(currentUser.getId()))
            .toList();

        if (activeMembers.isEmpty()) {
            archiveFamily(family.getId());
            updateUserFamilyId(currentUser.getId(), null);
            return;
        }

        UserEntity nextOwner = activeMembers.stream()
            .min(Comparator.comparing(UserEntity::getId))
            .orElseThrow();

        transferFamilyOwner(family.getId(), nextOwner.getId());
        updateUserFamilyId(currentUser.getId(), null);
    }

    private List<UserEntity> listActiveFamilyUsers(Long familyId) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getFamilyId, familyId)
            .eq(UserEntity::getStatus, ACTIVE_STATUS);
        return userMapper.selectList(wrapper);
    }

    private FamilyOverviewResponse buildFamilyOverview(UserEntity currentUser, FamilyEntity family) {
        List<FamilyMemberResponse> members = listActiveFamilyUsers(family.getId()).stream()
            .sorted(Comparator
                .comparing((UserEntity user) -> !user.getId().equals(family.getOwnerUserId()))
                .thenComparing(UserEntity::getId))
            .map(user -> new FamilyMemberResponse(
                user.getId(),
                user.getDisplayName(),
                user.getId().equals(family.getOwnerUserId()) ? "管理员" : "家庭成员",
                "已绑定",
                currentUser.getId().equals(family.getOwnerUserId()) && !user.getId().equals(currentUser.getId())
            ))
            .toList();

        return new FamilyOverviewResponse(true, family.getInviteCode(), members.size(), members);
    }

    private void updateUserFamilyId(Long userId, Long familyId) {
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .set(UserEntity::getFamilyId, familyId);
        userMapper.update(null, wrapper);
    }

    private void transferFamilyOwner(Long familyId, Long ownerUserId) {
        LambdaUpdateWrapper<FamilyEntity> wrapper = new LambdaUpdateWrapper<FamilyEntity>()
            .eq(FamilyEntity::getId, familyId)
            .set(FamilyEntity::getOwnerUserId, ownerUserId);
        familyMapper.update(null, wrapper);
    }

    private void archiveFamily(Long familyId) {
        LambdaUpdateWrapper<FamilyEntity> wrapper = new LambdaUpdateWrapper<FamilyEntity>()
            .eq(FamilyEntity::getId, familyId)
            .set(FamilyEntity::getStatus, ARCHIVED_STATUS);
        familyMapper.update(null, wrapper);
    }

    private void disableUserAccount(Long userId) {
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<UserEntity>()
            .eq(UserEntity::getId, userId)
            .set(UserEntity::getStatus, DISABLED_STATUS)
            .set(UserEntity::getFamilyId, null);
        userMapper.update(null, wrapper);
    }

    private String generateInviteCode() {
        for (int index = 0; index < 20; index += 1) {
            String candidate = INVITE_CODE_PREFIX + randomInviteCodeSuffix();
            if (findActiveFamilyByInviteCode(candidate).isEmpty()) {
                return candidate;
            }
        }

        throw new IllegalStateException("家庭邀请码生成失败，请稍后重试");
    }

    private String randomInviteCodeSuffix() {
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        for (int index = 0; index < INVITE_CODE_LENGTH; index += 1) {
            int nextIndex = secureRandom.nextInt(INVITE_CODE_CHARS.length());
            builder.append(INVITE_CODE_CHARS.charAt(nextIndex));
        }
        return builder.toString();
    }

    private String normalizeInviteCode(String inviteCode) {
        return inviteCode.trim().toUpperCase();
    }

    private UserAccount toUserAccount(UserEntity entity) {
        return new UserAccount(
            entity.getId(),
            entity.getUsername(),
            entity.getPhone(),
            entity.getEmail(),
            entity.getPasswordHash(),
            entity.getDisplayName(),
            entity.getAvatarUrl(),
            entity.getStatus(),
            entity.getRoleName(),
            entity.getFamilyId(),
            entity.getLastLoginAt()
        );
    }
}
