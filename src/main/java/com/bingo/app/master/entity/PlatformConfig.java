package com.bingo.app.master.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "platform_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformConfig {

    @Id
    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String value;
}
