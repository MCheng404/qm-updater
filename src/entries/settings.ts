/** 设置窗口入口（settings.html） */
import '../styles.css';
import { createApp } from 'vue';
import { sharedPlugin } from '../app/shared';
import SettingsView from '../views/SettingsView.vue';

createApp(SettingsView).use(sharedPlugin).mount('#app');
