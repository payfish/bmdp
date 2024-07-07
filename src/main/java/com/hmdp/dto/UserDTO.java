package com.hmdp.dto;

import com.hmdp.entity.User;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
