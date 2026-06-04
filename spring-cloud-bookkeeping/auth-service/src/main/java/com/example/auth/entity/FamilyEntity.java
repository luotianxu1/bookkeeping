package com.example.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("families")
public class FamilyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("invite_code")
    private String inviteCode;

    @TableField("family_name")
    private String familyName;

    private String status;
}
