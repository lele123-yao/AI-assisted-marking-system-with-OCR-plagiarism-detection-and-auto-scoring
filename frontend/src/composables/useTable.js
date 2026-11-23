import { reactive, ref } from 'vue'

/**
 * 表格分页通用逻辑。
 *
 * 后端统一分页返回 { total, current, size, pages, records }，
 * fetchFn 接收展开后的查询参数并返回该分页对象。
 *
 * @param {(params: object) => Promise<{ records: any[], total: number }>} fetchFn
 * @param {object} initialQuery 初始查询条件（不含分页字段）
 * @param {number} pageSize 默认每页条数
 */
export function useTable(fetchFn, initialQuery = {}, pageSize = 10) {
  const loading = ref(false)
  const list = ref([])
  const total = ref(0)
  const query = reactive({ pageNum: 1, pageSize, ...initialQuery })

  async function load() {
    loading.value = true
    try {
      const data = await fetchFn({ ...query })
      list.value = data?.records || []
      total.value = Number(data?.total || 0)
    } catch (error) {
      // 错误提示已由 request.js 全局处理，这里只保证表格回到空态
      list.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function search() {
    query.pageNum = 1
    return load()
  }

  function reset() {
    Object.keys(query).forEach((key) => {
      if (key === 'pageNum' || key === 'pageSize') return
      query[key] = initialQuery[key] ?? ''
    })
    query.pageNum = 1
    return load()
  }

  function handleSizeChange(size) {
    query.pageSize = size
    query.pageNum = 1
    load()
  }

  function handlePageChange(page) {
    query.pageNum = page
    load()
  }

  return {
    loading,
    list,
    total,
    query,
    load,
    search,
    reset,
    handleSizeChange,
    handlePageChange
  }
}
