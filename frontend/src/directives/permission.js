import { useUserStore } from '@/store/user'

/**
 * 按钮级权限指令
 * 用法：<el-button v-permission="'demo:add'">新增</el-button>
 * 无权限时直接从 DOM 中移除元素。
 */
export default {
  mounted(el, binding) {
    const store = useUserStore()
    if (!store.hasPermission(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
}
