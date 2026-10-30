const { chromium } = require('playwright-core');

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage({ viewport: { width: 430, height: 1400 }, deviceScaleFactor: 2 });

  const fs = require('fs');
  const tokenLine = fs.readFileSync('/tmp/jwt-tokens.txt', 'utf8').split('\n').find(l => l.startsWith('1 '));
  const token = tokenLine.split(' ')[1];

  // 注入登录态：路由守卫读 localStorage + access_token cookie
  await page.addInitScript(() => {
    localStorage.setItem('bookkeeping_current_user', JSON.stringify({
      id: 1, username: 'admin', nickname: 'admin',
    }));
  });
  await page.context().addCookies([{
    name: 'access_token', value: token, domain: 'localhost', path: '/',
  }]);

  await page.goto('http://localhost:5173/finance/stock-screener', { waitUntil: 'domcontentloaded' });

  // 等两张情绪卡片渲染出来
  await page.waitForSelector('.market-status-card', { timeout: 20000 });
  await page.waitForTimeout(2500);

  const card = page.locator('.market-status-card');
  await card.screenshot({ path: '/tmp/market-status-card.png' });

  const limit = page.locator('.limit-statistics-card');
  if (await limit.count()) {
    await limit.screenshot({ path: '/tmp/limit-card.png' });
    // 点展开按钮看展开态
    const toggle = page.locator('.limit-list-toggle');
    if (await toggle.count()) {
      await toggle.click();
      await page.waitForTimeout(600);
      await limit.screenshot({ path: '/tmp/limit-card-expanded.png' });
    }
  }

  await browser.close();
  console.log('screenshots done');
})().catch((e) => { console.error(e.message); process.exit(1); });
