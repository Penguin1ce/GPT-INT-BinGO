package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.entity.User;
import com.firefly.ragdemo.mapper.UserMapper;
import com.firefly.ragdemo.security.CustomUserPrincipal;
import com.firefly.ragdemo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        return new CustomUserPrincipal(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUserId(String userId) {
        Optional<User> user = userMapper.findById(userId);
        if (user.isPresent() && Boolean.TRUE.equals(user.get().getIsActive())) {
            return new CustomUserPrincipal(user.get());
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(String id) {
        return userMapper.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userMapper.existsByEmail(email);
    }

    @Override
    @Transactional
    public User save(User user) {
        // 若id为空，由数据库或上层生成；这里假设上层生成UUID
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    @Override
    @Transactional
    public void updateLastLogin(String userId) {
        userMapper.updateLastLogin(userId, LocalDateTime.now());
    }
} 
