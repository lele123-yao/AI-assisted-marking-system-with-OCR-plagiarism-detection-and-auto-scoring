import { menuTree } from '@/config/menu'
import { ROLE_LABELS, ROLE_PERMISSIONS } from '@/config/permission'

/**
 * 菜单权限数据源。
 *
 * 后端当前没有菜单 / 角色-权限关联接口（无 MenuController、无 sys_role_permission 接口），
 * 因此本文件不发起任何网络请求，只把前端菜单表与角色权限映射整理成
 * "菜单-角色"对照矩阵，供菜单权限页只读展示。
 * 实际授权请到「用户管理 → 分配角色」「角色管理」中操作。
 */

function roleHasPermission(roleCode, permission) {
  const list = ROLE_PERMISSIONS[roleCode] || []
  if (list.includes('*')) return true
  return !permission || list.includes(permission)
}

/** 生成菜单-角色权限矩阵 */
export function getMenuPermissionMatrix() {
  const rows = []

  menuTree.forEach((group) => {
    const menus = group.children?.length ? group.children : [group]
    menus.forEach((menu) => {
      if (menu.meta?.hidden) return
      const permission = menu.meta?.permission
      rows.push({
        module: group.children?.length ? group.meta.title : '基础功能',
        title: menu.meta?.title,
        path: menu.path,
        permission: permission || '-',
        roles: Object.keys(ROLE_PERMISSIONS).map((roleCode) => ({
          roleCode,
          roleName: ROLE_LABELS[roleCode],
          allowed: roleHasPermission(roleCode, permission)
        }))
      })
    })
  })

  return Promise.resolve(rows)
}
