import { test, expect } from '@playwright/test';

test('User can register and create a post', async ({ page }) => {
  const timestamp = Date.now();
  const username = `user_${timestamp}`;
  const email = `user_${timestamp}@example.com`;
  const password = 'Password123!';

  // 1. Go to register page
  await page.goto('/register');

  // 2. Fill registration form (MUI TextField renders <label> associated to the input
  // via aria, not a `label` HTML attribute, so match by accessible name)
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Email Address').fill(email);
  await page.getByLabel('Password').fill(password);

  // Submit registration
  await page.getByRole('button', { name: 'Sign Up' }).click();

  // 3. Should be redirected to feed
  await expect(page).toHaveURL('/');
  await expect(page.getByText(`What's on your mind, ${username}?`)).toBeVisible();

  // 4. Create a post
  const postContent = `Hello world from E2E test ${timestamp}`;
  await page.getByPlaceholder(`What's on your mind, ${username}?`).fill(postContent);
  await page.getByRole('button', { name: 'Post' }).click();

  // 5. Verify post appears in feed
  await expect(page.getByText(postContent)).toBeVisible();
});
