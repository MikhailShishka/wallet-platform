package com.wallet.repository;

import com.wallet.entity.OutboxRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxRecord, UUID> {

    /**
     * Выбирает батч непосланных записей, сортируя по времени создания.
     * findTop100 — ограничивает выборку.
     * sent_at IS NULL — только неотправленные.
     * OrderByCreatedAtAsc — сначала старые.
     */
    List<OutboxRecord> findTop100BySentAtIsNullOrderByCreatedAtAsc();
}
