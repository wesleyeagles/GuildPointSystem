package com.guild.app.dll.entity;

import com.guild.app.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Versao da DLL publicada no site; a mais recente e a que o botao de download entrega. */
@Entity
@Table(name = "dll_release")
@Getter
@Setter
public class DllRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, unique = true)
    private String version;

    private String notes;

    @Column(name = "file_name", nullable = false, length = 120)
    private String fileName;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_data", nullable = false)
    private byte[] fileData;

    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by_id")
    private Member publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt = Instant.now();
}
