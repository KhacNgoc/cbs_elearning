package com.bidv.coretraining.security;

import com.bidv.coretraining.access.service.PermissionCache;

import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final PermissionCache permissionCache;
    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository,
            PermissionCache permissionCache) {
        this.userRepository = userRepository;
        this.permissionCache = permissionCache;
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (permissionCache != null)
            permissionCache.attach(user);
        return principal(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        if (permissionCache != null)
            permissionCache.attach(user);
        return principal(user);
    }

    private CustomUserDetails principal(User user) {
        // Authentication metadata is read after this transaction has closed.
        // Legacy users have no dynamic role names; do not leave a lazy fallback.
        if (!user.isDynamicAccessInitialized())
            user.setPolicySnapshot(java.util.List.of());
        return new CustomUserDetails(user);
    }
}
