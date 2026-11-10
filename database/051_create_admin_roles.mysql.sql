-- PC 管理端角色；保留 users.role_name 作为单角色关联，兼容移动端及已有用户。
-- 本文件只创建新增表和缺失的内置角色，不更新已有用户或角色授权。
CREATE TABLE IF NOT EXISTS admin_roles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  built_in BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_roles_code (code),
  CONSTRAINT chk_admin_roles_status CHECK (status IN ('active', 'disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_role_permissions (
  role_id BIGINT UNSIGNED NOT NULL,
  permission_code VARCHAR(80) NOT NULL COMMENT '由服务端权限目录定义的可执行权限',
  PRIMARY KEY (role_id, permission_code),
  CONSTRAINT fk_admin_role_permissions_role FOREIGN KEY (role_id) REFERENCES admin_roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO admin_roles (code, name, description, built_in)
SELECT 'super_admin', '超级管理员', '拥有全部 PC 管理权限，内置角色不可修改', TRUE
WHERE NOT EXISTS (SELECT 1 FROM admin_roles WHERE code = 'super_admin');

INSERT INTO admin_roles (code, name, description, built_in)
SELECT 'user', '普通用户', '使用移动端业务，默认不具备 PC 管理权限', TRUE
WHERE NOT EXISTS (SELECT 1 FROM admin_roles WHERE code = 'user');
