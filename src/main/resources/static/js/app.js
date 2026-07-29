/**
 * FishingTime 前端脚本
 * 页面通过 AJAX 调用 /api/** 接口，与后端完全分离
 */

// ──────────────────────────────────────────
// 通用 API 请求
// ──────────────────────────────────────────

async function apiPost(url, data) {
    const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
        credentials: 'same-origin'
    });
    return res.json();
}

async function apiGet(url) {
    const res = await fetch(url, {
        method: 'GET',
        credentials: 'same-origin'
    });
    return res.json();
}

// ──────────────────────────────────────────
//  首页
// ──────────────────────────────────────────

function initIndexPage() {
    const navRight = document.getElementById('navRight');
    const welcomeContent = document.getElementById('welcomeContent');
    if (!navRight) return;

    apiGet('/api/auth/current-user').then(resp => {
        if (resp.code === 200 && resp.data) {
            const user = resp.data;
            navRight.innerHTML = `
                <span style="color:#333">${user.nickname}</span>
                <a href="javascript:void(0)" onclick="logout()">退出</a>
            `;
            if (welcomeContent) {
                welcomeContent.innerHTML = `<p>你好，${user.nickname}！欢迎回来。</p>`;
            }
        } else {
            navRight.innerHTML = `<a href="/login">登录</a><a href="/register">注册</a>`;
            if (welcomeContent) {
                welcomeContent.innerHTML = `<p><a href="/login">登录</a> 或 <a href="/register">注册</a> 后开始使用。</p>`;
            }
        }
    }).catch(() => {
        navRight.innerHTML = `<a href="/login">登录</a><a href="/register">注册</a>`;
    });
}

// ──────────────────────────────────────────
// 登录
// ──────────────────────────────────────────

function initLoginPage() {
    const form = document.getElementById('loginForm');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;

        const resp = await apiPost('/api/auth/login', { username, password });
        const errorMsg = document.getElementById('errorMsg');

        if (resp.code === 200) {
            window.location.href = '/';
        } else {
            errorMsg.textContent = resp.message || '登录失败';
            errorMsg.style.display = 'block';
        }
    });
}

// ──────────────────────────────────────────
// 注册
// ──────────────────────────────────────────

function initRegisterPage() {
    const form = document.getElementById('registerForm');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('username').value.trim();
        const nickname = document.getElementById('nickname').value.trim();
        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value;

        const resp = await apiPost('/api/auth/register', { username, nickname, email, password });
        const errorMsg = document.getElementById('errorMsg');

        if (resp.code === 200) {
            // 注册成功后跳转到登录页
            window.location.href = '/login';
        } else {
            errorMsg.textContent = resp.message || '注册失败';
            errorMsg.style.display = 'block';
        }
    });
}

// ──────────────────────────────────────────
// 退出
// ──────────────────────────────────────────

async function logout() {
    await apiPost('/api/auth/logout', {});
    window.location.href = '/';
}

// ──────────────────────────────────────────
// 按页面初始化
// ──────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    initIndexPage();
    initLoginPage();
    initRegisterPage();
});
