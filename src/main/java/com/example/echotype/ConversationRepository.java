package com.example.echotype;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserId(Long userId);
    List<Conversation> findByConversationId(String conversationId);
    void deleteByConversationId(String conversationId);
}
