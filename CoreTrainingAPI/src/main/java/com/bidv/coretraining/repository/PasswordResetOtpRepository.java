package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.PasswordResetOtp;
import com.bidv.coretraining.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findByUserAndOtpCode(User user, String otpCode);
    Optional<PasswordResetOtp> findByUser(User user);
    void deleteByUser(User user);
}
