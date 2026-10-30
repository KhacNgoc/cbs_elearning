package com.bidv.coretraining.service;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.RagConversationMessageRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest(showSql=false,properties={"spring.config.location=classpath:access-test.yml","spring.sql.init.mode=never"})
class RagHistoryRepositoryTest {
 @Autowired EntityManager em;@Autowired RagConversationMessageRepository repository;
 @Test void pagesCompleteExchangesIgnoresUnansweredQuestionsAndConsecutiveAssistants(){
  var d=TrainingDomain.builder().id("RD").code("RD").name("Domain").build();em.persist(d);var p=TrainingProgram.builder().id("RP").code("RP").name("Program").domain(d).build();em.persist(p);var c=Course.builder().id("RC").code("RC").name("Course").program(p).build();em.persist(c);var u=User.builder().username("chat").fullName("Chat").password("x").role("ROLE_USER").build();em.persist(u);
  var conversation=RagConversation.builder().id("conversation").user(u).course(c).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();em.persist(conversation);
  for(int i=0;i<25;i++){message(conversation,"user","Q"+i);message(conversation,"assistant","A"+i);}
  message(conversation,"assistant","orphan");message(conversation,"user","pending");em.flush();
  var page=repository.findExchangeIds("conversation",Long.MAX_VALUE,PageRequest.of(0,21));assertThat(page).hasSize(21);
  var older=repository.findExchangeIds("conversation",((Number)page.get(19)[0]).longValue(),PageRequest.of(0,21));assertThat(older).hasSize(5);
  assertThat(repository.findExchangeIds("other",Long.MAX_VALUE,PageRequest.of(0,21))).isEmpty();
  var last=em.find(RagConversationMessage.class,((Number)page.get(0)[0]).longValue());assertThat(last.getContent()).isEqualTo("A24");
 }
 private void message(RagConversation c,String role,String content){em.persist(RagConversationMessage.builder().conversation(c).role(role).content(content).createdAt(LocalDateTime.now()).build());}
}
