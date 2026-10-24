/** 主窗口入口（index.html） */
import '../styles.css';
import { createApp } from 'vue';
import { sharedPlugin } from '../app/shared';
import MainView from '../views/MainView.vue';

createApp(MainView).use(sharedPlugin).mount('#app');
