package com.scalecart.user.dto;

import com.scalecart.user.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean enabled;
    private LocalDateTime createdAt;
}
