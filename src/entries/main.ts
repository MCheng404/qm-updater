/** 主窗口入口（index.html）：单窗口内包含 更新 / 设置 / 关于 三个视图 */
import '../styles.css';
import { createApp } from 'vue';
import { sharedPlugin } from '../app/shared';
import App from '../App.vue';

createApp(App).use(sharedPlugin).mount('#app');
