package com.guild.app.log.repository;

import com.guild.app.log.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Lista filtrada da aba Logs (tipo, evento, player). */
    @Override
    @EntityGraph(attributePaths = {"actor", "target"})
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);

    /** Nomes de evento (objetivo) que aparecem nos logs, para o filtro "Evento". */
    @Query(value = "SELECT DISTINCT payload->>'objectiveName' AS name FROM audit_log "
            + "WHERE payload->>'objectiveName' IS NOT NULL ORDER BY name", nativeQuery = true)
    List<String> findObjectiveNames();
}
