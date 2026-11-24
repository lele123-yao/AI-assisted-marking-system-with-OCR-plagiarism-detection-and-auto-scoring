import { computed, reactive, ref } from 'vue'

/**
 * 移动端分页列表。
 *
 * 与桌面 useTable 的区别：移动端采用「加载更多」，loadMore 把新一页追加到 list；
 * 切换查询条件时调用 search()，重新从第 1 页开始。
 *
 * @param {(params: object) => Promise<{ records?: any[], total?: number }>} fetchFn
 * @param {object} initialQuery 初始查询条件（不含分页字段）
 * @param {number} pageSize 每页条数
 */
export function useMobileList(fetchFn, initialQuery = {}, pageSize = 10) {
  const query = reactive({ pageNum: 1, pageSize, ...initialQuery })
  const list = ref([])
  const total = ref(0)
  const loading = ref(false)

  const hasMore = computed(() => list.value.length < total.value)
  const finished = computed(() => !hasMore.value && list.value.length > 0)

  async function fetchPage(pageNum, append) {
    if (loading.value) return
    loading.value = true
    try {
      const data = await fetchFn({ ...query, pageNum })
      const records = data?.records || []
      total.value = Number(data?.total || 0)
      list.value = append ? [...list.value, ...records] : records
      query.pageNum = pageNum
    } catch {
      // 错误提示由 request.js 统一处理，这里只保证列表不会停留在错误状态
      if (!append) {
        list.value = []
        total.value = 0
      }
    } finally {
      loading.value = false
    }
  }

  /** 重新加载第 1 页 */
  function load() {
    return fetchPage(1, false)
  }

  /** 刷新（保留当前查询条件） */
  function refresh() {
    return fetchPage(1, false)
  }

  /** 加载下一页并追加 */
  function loadMore() {
    if (!hasMore.value) return Promise.resolve()
    return fetchPage(query.pageNum + 1, true)
  }

  /** 修改条件后重新查询 */
  function search() {
    return fetchPage(1, false)
  }

  /** 重置查询条件 */
  function reset() {
    Object.keys(query).forEach((key) => {
      if (key === 'pageNum' || key === 'pageSize') return
      query[key] = initialQuery[key] ?? ''
    })
    return fetchPage(1, false)
  }

  return { query, list, total, loading, hasMore, finished, load, refresh, loadMore, search, reset }
}
