/** 关于窗口入口（about.html） */
import '../styles.css';
import { createApp } from 'vue';
import { sharedPlugin } from '../app/shared';
import AboutView from '../views/AboutView.vue';

createApp(AboutView).use(sharedPlugin).mount('#app');
