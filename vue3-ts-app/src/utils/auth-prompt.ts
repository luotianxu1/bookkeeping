import { ref } from 'vue'

export const authPromptVisible = ref(false)
export const authPromptRedirect = ref('')

export function showAuthPrompt(redirect = '') {
  authPromptRedirect.value = redirect
  authPromptVisible.value = true
}

export function hideAuthPrompt() {
  authPromptVisible.value = false
}
