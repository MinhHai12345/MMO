package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.SocialPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
}
