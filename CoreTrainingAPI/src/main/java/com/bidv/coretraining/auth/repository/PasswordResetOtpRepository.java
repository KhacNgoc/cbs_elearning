package com.bidv.coretraining.auth.repository;

import com.bidv.coretraining.auth.entity.PasswordResetOtp;
import com.bidv.coretraining.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findByUserAndOtpCode(User user, String otpCode);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetOtp> findByUser(User user);

    void deleteByUser(User user);
}
