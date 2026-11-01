// 全局操作反馈弹窗（CommonFeedback）的状态与开关：多个页面共用的三件套。
import { ref } from 'vue'

export type FeedbackType = 'success' | 'error'

export function useFeedback() {
  const showFeedbackModal = ref(false)
  const feedbackMessage = ref('')
  const feedbackType = ref<FeedbackType>('success')

  function showFeedback(message: string, type: FeedbackType) {
    feedbackMessage.value = message
    feedbackType.value = type
    showFeedbackModal.value = true
  }

  return {
    showFeedbackModal,
    feedbackMessage,
    feedbackType,
    showFeedback,
  }
}
