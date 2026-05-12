<script setup lang="ts">
// 我的页：展示用户信息和个人设置入口。
type ProfileMenuItem = {
  label: string
}

const profileMenus: ProfileMenuItem[] = [
  { label: '隐私与安全' },
  { label: '消息通知' },
  { label: '关于我们' },
]
</script>

<template>
  <section class="profile-page" aria-label="我的页面">
    <section class="profile-user-card" aria-label="用户信息">
      <div class="avatar">LT</div>
      <h1>罗天旭</h1>
    </section>

    <section class="profile-menu-card" aria-label="我的功能列表">
      <button
        v-for="(item, index) in profileMenus"
        :key="item.label"
        class="menu-item"
        type="button"
      >
        <span>{{ item.label }}</span>
        <span class="arrow">></span>
        <span v-if="index < profileMenus.length - 1" class="divider" aria-hidden="true"></span>
      </button>
    </section>

    <button class="logout-button" type="button">退出登录</button>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
