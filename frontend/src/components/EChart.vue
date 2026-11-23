<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts 通用容器。
 * - option 变化时整体重绘（notMerge=true，避免残留旧系列）
 * - 监听容器尺寸变化自动 resize
 * - 组件卸载时 dispose，防止内存泄漏
 */
const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' }
})

const chartRef = ref(null)
let chart = null
let observer = null

function render() {
  if (!chart) return
  chart.setOption(props.option || {}, true)
}

onMounted(() => {
  chart = echarts.init(chartRef.value)
  render()
  observer = new ResizeObserver(() => chart && chart.resize())
  observer.observe(chartRef.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="chartRef" class="echart" :style="{ height }" />
</template>

<style scoped>
.echart {
  width: 100%;
}
</style>
