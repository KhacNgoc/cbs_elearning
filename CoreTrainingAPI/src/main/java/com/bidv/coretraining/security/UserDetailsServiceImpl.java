package com.bidv.coretraining.security;

import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.PermissionCache permissionCache;
    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly=true)
    public UserDetails loadUserById(Long id) {
        User user=userRepository.findById(id).orElseThrow(()->new UsernameNotFoundException("User not found"));
        if(permissionCache!=null)permissionCache.attach(user);
        return new CustomUserDetails(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        if(permissionCache!=null)permissionCache.attach(user);
        return new CustomUserDetails(user);
    }
}
