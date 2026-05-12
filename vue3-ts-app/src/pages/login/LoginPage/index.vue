<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const phone = ref('')
const code = ref('')

function submitLogin() {
  void phone.value
  void code.value
  router.push('/finance')
}
</script>

<template>
  <section class="login-page" aria-label="登录">
    <header class="login-header">
      <h1>登录</h1>
      <p>继续记录你们的账单、菜单、纪念日和每一天的小事。</p>
    </header>

    <div class="login-form-wrap">
      <section class="login-form-card">
        <label class="field">
          <span>手机号</span>
          <input v-model="phone" type="tel" inputmode="numeric" placeholder="请输入手机号" />
        </label>

        <div class="field">
          <span>验证码</span>
          <div class="code-line">
            <input v-model="code" type="text" placeholder="输入验证码" />
            <button type="button" class="code-btn">获取验证码</button>
          </div>
        </div>
      </section>

      <div class="login-actions">
        <button type="button" class="login-btn" @click="submitLogin">登录并继续</button>
        <button type="button" class="wx-btn">微信快捷登录</button>
      </div>
      <p class="agreement">登录即表示你已阅读并同意《用户协议》和《隐私政策》</p>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
