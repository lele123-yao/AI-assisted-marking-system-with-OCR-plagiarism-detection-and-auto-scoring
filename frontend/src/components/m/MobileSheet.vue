<script setup>
import { computed } from 'vue'

/**
 * 移动端底部弹层表单容器。
 * 统一「取消 / 确定」按钮与安全区，页面只关心表单内容。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  height: { type: String, default: '72%' },
  confirmText: { type: String, default: '确定' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

function handleCancel() {
  visible.value = false
  emit('cancel')
}
</script>

<template>
  <el-drawer
    v-model="visible"
    direction="btt"
    :size="height"
    :title="title"
    class="m-sheet"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="m-sheet-body">
      <slot />
    </div>
    <template #footer>
      <div class="m-sheet-footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="loading" @click="emit('confirm')">
          {{ confirmText }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>
