//! 运行日志落盘。
//!
//! 日志按天写到 `%LOCALAPPDATA%\qm-updater\logs\YYYY-MM-DD.log`，
//! 供更新失败后事后排查（设置里的「打开日志文件夹」也指向这里）。
//!
//! 设计原则：写日志失败一律静默 —— 它只是辅助手段，绝不能影响更新主流程。

use crate::model::LogLine;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::PathBuf;
use std::sync::Mutex;

/// 日志目录：`%LOCALAPPDATA%\qm-updater\logs`
pub fn log_dir() -> Option<PathBuf> {
    let local = std::env::var("LOCALAPPDATA").ok()?;
    Some(PathBuf::from(local).join("qm-updater").join("logs"))
}

/// 今天日志文件的完整路径
pub fn today_file() -> Option<PathBuf> {
    Some(log_dir()?.join(format!("{}.log", today())))
}

/// 当日文件句柄缓存：`(日期, 文件)`。
///
/// check_updates 同时跑四个源线程、更新时又逐行打 cmd 日志 —— 高频 append。
/// 原来每次 append 都 open/close 一次文件，Windows 上 OpenOptions 的成本
/// （目录查找 + 句柄分配）在高频日志下会被放大。这里缓存当日句柄，
/// 跨天（today() 变化）或打开失败时自动重开。
static FILE_CACHE: Mutex<Option<(String, fs::File)>> = Mutex::new(None);

/// 追加一行日志（失败静默）
pub fn append(line: &LogLine) {
    let Some(path) = today_file() else {
        return;
    };
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    // 先在内存里拼出完整的一整行，再一次性写入。
    //
    // 直接 `writeln!(f, "{} [{}] {}", ..)` 会被拆成**多次** write 系统调用
    // （每个格式参数一次），而 Windows 的 FILE_APPEND_DATA 只保证**单次写**
    // 的原子性，保证不了一个逻辑行的原子性。check_updates 同时跑四个源线程
    // （npm / winget / pip / openclaw），谁都在高频调 emit_log —— 实测四线程
    // 各写 300 行时有 43% 的行被撕成半截交错。
    // 而这个文件存在的唯一理由就是"更新失败后事后排查"，撕坏了就毫无价值。
    let buf = format!(
        "{} [{}] {}\n",
        line.at,
        line.level.to_uppercase(),
        line.text
    );

    let date = today();
    // Mutex 中毒（别的线程 panic 时）就丢弃旧句柄重建，日志不能因此停写
    let mut cache = match FILE_CACHE.lock() {
        Ok(g) => g,
        Err(p) => p.into_inner(),
    };

    let needs_reopen = match cache.as_ref() {
        Some((d, _)) => d != &date,
        None => true,
    };
    if needs_reopen {
        let f = match OpenOptions::new().create(true).append(true).open(&path) {
            Ok(f) => f,
            Err(_) => return,
        };
        *cache = Some((date, f));
    }

    if let Some((_, f)) = cache.as_mut() {
        let _ = f.write_all(buf.as_bytes());
    }
}

/// 本地日期 `YYYY-MM-DD`（跟随系统时区，与 util::now_time 同源）
fn today() -> String {
    let n = crate::util::local_now();
    format!("{:04}-{:02}-{:02}", n.year, n.month, n.day)
}
