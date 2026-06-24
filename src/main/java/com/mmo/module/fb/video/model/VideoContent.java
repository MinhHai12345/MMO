package com.mmo.module.fb.video.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class VideoContent {

    private String hook;

    private String title;

    private String prediction;

    private String confidence;

    private String edge;

    private String score;
}
