package com.bidv.coretraining.repository;
import com.bidv.coretraining.entity.BankQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BankQuestionRepository extends JpaRepository<BankQuestion,Long> {
    boolean existsByCodeIgnoreCaseAndIdNot(String code,Long id);
}
