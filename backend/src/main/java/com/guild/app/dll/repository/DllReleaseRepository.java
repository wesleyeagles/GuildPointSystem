package com.guild.app.dll.repository;

import com.guild.app.dll.dto.DllReleaseView;
import com.guild.app.dll.dto.DllUpdateManifest;
import com.guild.app.dll.entity.DllRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DllReleaseRepository extends JpaRepository<DllRelease, Long> {

    /** Sem o arquivo: a lista e o aviso nao precisam dos bytes. */
    @Query("""
            select new com.guild.app.dll.dto.DllReleaseView(r.id, r.version, r.notes, r.fileName, r.fileSize,
                    p.nickname, r.publishedAt, case when r.updateSha256 is not null then true else false end)
            from DllRelease r left join r.publishedBy p
            order by r.publishedAt desc, r.id desc
            """)
    List<DllReleaseView> findAllViews();

    /** Versoes com atualizacao automatica, mais recente primeiro (sem os bytes). */
    @Query("""
            select new com.guild.app.dll.dto.DllUpdateManifest(r.version, r.updateSha256, r.updateSignature,
                    r.updateSize)
            from DllRelease r
            where r.updateSha256 is not null
            order by r.publishedAt desc, r.id desc
            """)
    List<DllUpdateManifest> findUpdateManifests();

    Optional<DllRelease> findByVersion(String version);

    boolean existsByVersion(String version);
}
