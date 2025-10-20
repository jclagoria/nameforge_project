package com.forge.adapters.outbound.database.entity;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table("generated_usernames")
public class UsernameEntity {

    @Id
    @EqualsAndHashCode.Include
    private Long id;

    @Column("username")
    private String username;

    @Column("language")
    private Language language;

    @Column("pattern_type")
    private PatternType patternType;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("is_used")
    private boolean isUsed;

    @Column("used_at")
    private LocalDateTime usedAt;

}
