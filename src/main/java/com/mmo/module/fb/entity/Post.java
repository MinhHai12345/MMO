//package com.mmo.entity;
//
//import com.mmo.entity.abs.AbstractEntity;
//import com.mmo.entity.enums.Visibility;
//import jakarta.persistence.Column;
//import jakarta.persistence.Entity;
//import jakarta.persistence.EnumType;
//import jakarta.persistence.Enumerated;
//import jakarta.persistence.ManyToOne;
//import jakarta.persistence.Table;
//import lombok.Getter;
//import lombok.Setter;
//
//@Entity
//@Table(name = "posts")
//@Getter
//@Setter
//public class Post extends AbstractEntity {
//
//    @ManyToOne
//    private Match match;
//
//    @Column
//    private String title;
//
//    @Column(columnDefinition = "TEXT")
//    private String content;
//
//    @Enumerated(EnumType.STRING)
//    private Visibility visibility;
//
//}