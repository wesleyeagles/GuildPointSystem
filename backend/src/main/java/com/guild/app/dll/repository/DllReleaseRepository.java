package com.guild.app.dll.repository;

import com.guild.app.dll.dto.DllReleaseView;
import com.guild.app.dll.entity.DllRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DllReleaseRepository extends JpaRepository<DllRelease, Long> {

    /** Sem o arquivo: a lista e o aviso nao precisam dos bytes. */
    @Query("""
            select new com.guild.app.dll.dto.DllReleaseView(r.id, r.version, r.notes, r.fileName, r.fileSize,
                    p.nickname, r.publishedAt)
            from DllRelease r left join r.publishedBy p
            order by r.publishedAt desc, r.id desc
            """)
    List<DllReleaseView> findAllViews();

    boolean existsByVersion(String version);
}
