package com.mmo.module.fb.publish.repository;

import com.mmo.module.fb.publish.entity.SocialPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
}
