package com.enterpriseflow.project;

import com.enterpriseflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Join entity rather than {@code @ManyToMany}: the membership has its own data (joinedAt) and its own
 * lifecycle, and a plain join table could hold neither.
 */
@Entity
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant joinedAt;

    protected ProjectMember() {
        // Required by JPA
    }

    ProjectMember(Project project, User user, Instant joinedAt) {
        this.project = project;
        this.user = user;
        this.joinedAt = joinedAt;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public User getUser() {
        return user;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
