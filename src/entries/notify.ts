/** 通知窗口入口（notify.html） */
import '../styles.css';
import { createApp } from 'vue';
import { sharedPlugin } from '../app/shared';
import NotifyView from '../views/NotifyView.vue';

createApp(NotifyView).use(sharedPlugin).mount('#app');
