use crate::tr;
use std::cmp::Ordering;
use std::io::{BufRead, BufReader};
use std::path::PathBuf;
use std::process::{Command, Stdio};
use std::sync::{mpsc, RwLock};
use std::time::Duration;

#[cfg(windows)]
use std::os::windows::process::CommandExt;

#[cfg(windows)]
pub const CREATE_NO_WINDOW: u32 = 0x0800_0000;

/// is_admin 结果缓存：应用运行期间管理员状态不会变化，
/// 避免每次 OpenClaw 更新都启动 `net session` 子进程。
static ADMIN_CACHE: RwLock<Option<bool>> = RwLock::new(None);

/// 子进程输出解码：优先 UTF-8，失败回退 GBK（Windows 中文环境常见）
pub fn decode(bytes: &[u8]) -> String {
    match std::str::from_utf8(bytes) {
        Ok(s) => s.to_string(),
        Err(_) => {
            let (cow, _, _) = encoding_rs::GBK.decode(bytes);
            cow.into_owned()
        }
    }
}

/// 构造子进程。Windows 下统一走 cmd /C，兼容 npm.cmd / pip.exe / winget.exe 等。
fn base_command(program: &str, args: &[&str]) -> Command {
    let mut cmd = Command::new("cmd");
    cmd.arg("/D").arg("/C").arg(program);
    for a in args {
        cmd.arg(a);
    }
    // 让 Python 系工具输出 UTF-8
    cmd.env("PYTHONIOENCODING", "utf-8");
    cmd.env("PYTHONUTF8", "1");

    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);

    cmd
}

/// 同上，但**把退出码一并带回**：返回 `(退出码, 输出)`。
///
/// 为什么需要它：原 `run_capture*` 只看 stdout，退出码彻底丢失。
/// 于是在三个更新源里，"命令跑起来了但失败了"（registry 离线、依赖树损坏、
/// winget 源不可用）与"确实没有可更新项"变成同一件事 ——
/// 解析不出 JSON → 返回空列表 → 打一条**绿色 OK** 的"全部是最新版本"。
/// 那个"命令失败"的分支只在 spawn 失败/超时时才走得到。
///
/// 退出码非 0 时 stdout 往往是要害信息，所以两种情况都把输出带回去。
pub fn run_capture_status(
    program: &str,
    args: &[&str],
    timeout_secs: u64,
) -> Result<(i32, String), String> {
    let out = run_capture_raw(program, args, timeout_secs)?;
    Ok((out.0, out.1))
}

/// 带超时的同步执行并捕获 stdout（失败时回退 stderr，两路合并）
pub fn run_capture_with_timeout(
    program: &str,
    args: &[&str],
    timeout_secs: u64,
) -> Result<String, String> {
    run_capture_status(program, args, timeout_secs).map(|(_, text)| text)
}

/// `run_capture_status` 的实现：返回 `(退出码, 输出文本)`。
///
/// 直接复用 `run_stream_with_timeout`：它已经处理了「总超时检查在循环最外层、
/// 超时后 kill 子进程、退出后排空剩余输出」这三件事。原实现是 `Command::output()`
/// 加 `recv_timeout` —— 超时返回后 spawn 的线程还挂在 `output()` 上等子进程，
/// 子进程无人接管、继续跑到自然结束（孤儿进程），只读查询尚可容忍，
/// 一旦将来用于更新类命令就会在超时后继续产生副作用。
fn run_capture_raw(
    program: &str,
    args: &[&str],
    timeout_secs: u64,
) -> Result<(i32, String), String> {
    let mut lines: Vec<String> = Vec::new();
    let code = run_stream_with_timeout(program, args, timeout_secs, |line| {
        lines.push(line.to_string());
    })?;
    Ok((code, lines.join("\n")))
}

/// 流式执行，逐行回调；返回进程退出码。默认 120 秒超时。
pub fn run_stream<F: FnMut(&str)>(program: &str, args: &[&str], on_line: F) -> Result<i32, String> {
    run_stream_with_timeout(program, args, 120, on_line)
}

/// 带超时的流式执行，逐行回调；返回进程退出码。
///
/// 优化：用 250ms 轮询替代 100ms，减少 CPU 唤醒次数；
/// 有输出时立即处理（recv_timeout 返回 Ok），无输出时才轮询检查子进程。
pub fn run_stream_with_timeout<F: FnMut(&str)>(
    program: &str,
    args: &[&str],
    timeout_secs: u64,
    mut on_line: F,
) -> Result<i32, String> {
    let mut child = base_command(program, args)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| tr!("util.spawnFailed", program, e))?;

    let stdout = child.stdout.take();
    let stderr = child.stderr.take();

    let (tx, rx) = mpsc::channel::<Vec<u8>>();

    // stdout 读取线程
    if let Some(out) = stdout {
        let tx = tx.clone();
        std::thread::spawn(move || {
            let mut reader = BufReader::new(out);
            loop {
                let mut buf = Vec::new();
                match reader.read_until(b'\n', &mut buf) {
                    Ok(0) => break,
                    Ok(_) => {
                        if tx.send(buf).is_err() {
                            break;
                        }
                    }
                    Err(_) => break,
                }
            }
        });
    }

    // stderr 读取线程
    if let Some(err) = stderr {
        let tx = tx.clone();
        std::thread::spawn(move || {
            let mut reader = BufReader::new(err);
            loop {
                let mut buf = Vec::new();
                match reader.read_until(b'\n', &mut buf) {
                    Ok(0) => break,
                    Ok(_) => {
                        if tx.send(buf).is_err() {
                            break;
                        }
                    }
                    Err(_) => break,
                }
            }
        });
    }

    drop(tx);

    let deadline = std::time::Instant::now() + Duration::from_secs(timeout_secs);
    let poll_interval = Duration::from_millis(250);

    // 接收输出 + 等待子进程，带总超时
    loop {
        // 总超时检查必须在循环最外层，且与"有没有新输出"无关。
        //
        // 原来它只写在 `RecvTimeoutError::Timeout` 分支里，也就是"连续 250ms 没有
        // 新输出"才检查。而 npm install / pip install / winget upgrade 这些恰恰
        // 是边干活边刷进度的 —— recv_timeout 一直返回 Ok，deadline 永远轮不到
        // 被检查，120 秒总超时形同虚设。调用方 start_update 又是串行 await
        // 每项更新，于是任何"卡住但仍在输出"的子进程都会让整个更新永久挂死，
        // 前端连超时错误都收不到。
        if std::time::Instant::now() > deadline {
            let _ = child.kill();
            let _ = child.wait();
            return Err(tr!("util.timeout", program, timeout_secs));
        }

        match rx.recv_timeout(poll_interval) {
            Ok(raw) => {
                let line = decode(&raw);
                let line = line.trim_end_matches(['\r', '\n']);
                on_line(line);
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {
                // 250ms 内无新输出，检查子进程是否已退出
                match child.try_wait() {
                    Ok(Some(status)) => {
                        // 子进程已退出，排空通道中剩余的输出
                        for raw in rx.try_iter() {
                            let line = decode(&raw);
                            let line = line.trim_end_matches(['\r', '\n']);
                            on_line(line);
                        }
                        return Ok(status.code().unwrap_or(-1));
                    }
                    Ok(None) => {
                        // 子进程仍在运行，继续循环（超时由上面的外层检查负责）
                    }
                    Err(e) => return Err(tr!("util.waitFailed", program, e)),
                }
            }
            Err(mpsc::RecvTimeoutError::Disconnected) => {
                // 所有读取线程已退出，等待子进程结束
                let status = child
                    .wait()
                    .map_err(|e| tr!("util.waitExitFailed", program, e))?;
                return Ok(status.code().unwrap_or(-1));
            }
        }
    }
}

/// 执行一段 PowerShell 脚本并返回 stdout。
///
/// **为什么不用 `-Command "<脚本>"`**：`base_command` 会再套一层 `cmd /D /C`，
/// 而 cmd 有自己的解析规则（不认 `\"`、把换行当命令分隔）。多行脚本 +
/// 引号经过这一层会被截断或改写，脚本静默失败——症状就是"读取不到系统字体"、
/// 图标注册表扫描永远返回空。
///
/// 落盘成临时 .ps1 再用 `-File` 执行，可以彻底绕开所有引号/换行转义问题。
pub fn run_powershell(script: &str, timeout_secs: u64) -> Result<String, String> {
    let path = temp_script_path();

    // PowerShell 5.1 读不带 BOM 的文件会按 ANSI 解码，中文脚本会乱码，
    // 所以必须写入 UTF-8 BOM。
    let mut bytes = vec![0xEF, 0xBB, 0xBF];
    bytes.extend_from_slice(script.as_bytes());
    std::fs::write(&path, bytes).map_err(|e| tr!("util.writeScriptFailed", e))?;

    let path_str = path.to_string_lossy().to_string();
    let result = run_capture_with_timeout(
        "powershell",
        &[
            "-NoProfile",
            "-NonInteractive",
            // CREATE_NO_WINDOW 只作用于直接子进程（cmd）。
            // powershell.exe 是控制台程序，作为"孙进程"启动时会自己要一个控制台，
            // 于是冒出闪烁的黑窗——必须显式让它隐藏窗口。
            "-WindowStyle",
            "Hidden",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            &path_str,
        ],
        timeout_secs,
    );

    let _ = std::fs::remove_file(&path);
    result
}

fn temp_script_path() -> PathBuf {
    let dir = std::env::var("TEMP").unwrap_or_else(|_| ".".to_string());
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    PathBuf::from(dir).join(format!("qm-{}-{}.ps1", std::process::id(), nanos))
}

/// 命令是否可用
pub fn cmd_exists(program: &str) -> bool {
    which::which(program).is_ok()
}

/// 是否管理员权限（带缓存，5 秒超时，超时视为非管理员）
///
/// 优化：结果缓存到 ADMIN_CACHE，应用运行期间只检测一次，
/// 避免每次 OpenClaw 更新都启动 `net session` 子进程。
pub fn is_admin() -> bool {
    // 快速路径：读缓存
    if let Ok(guard) = ADMIN_CACHE.read() {
        if let Some(cached) = *guard {
            return cached;
        }
    }

    // 慢速路径：检测并缓存
    let result = detect_admin();
    if let Ok(mut guard) = ADMIN_CACHE.write() {
        *guard = Some(result);
    }
    result
}

fn detect_admin() -> bool {
    let (tx, rx) = mpsc::channel::<bool>();
    std::thread::spawn(move || {
        let mut cmd = Command::new("net");
        cmd.arg("session").stdout(Stdio::null()).stderr(Stdio::null());
        #[cfg(windows)]
        cmd.creation_flags(CREATE_NO_WINDOW);
        let result = cmd.status().map(|s| s.success()).unwrap_or(false);
        let _ = tx.send(result);
    });
    rx.recv_timeout(Duration::from_secs(5))
        .unwrap_or(false)
}

/// 本地时间的各字段（日志时间戳与按天分文件的日期都从这里取）。
///
/// Windows 用 `GetLocalTime`（跟随系统时区，含夏令时）；非 Windows
/// 回退到 UTC 分解 —— 本应用是 Windows-only，这里只是保证代码可编译。
pub struct LocalNow {
    pub year: u32,
    pub month: u32,
    pub day: u32,
    pub hour: u32,
    pub minute: u32,
    pub second: u32,
}

pub fn local_now() -> LocalNow {
    #[cfg(windows)]
    {
        use std::mem::MaybeUninit;

        #[repr(C)]
        struct Systemtime {
            w_year: u16,
            w_month: u16,
            w_day_of_week: u16,
            w_day: u16,
            w_hour: u16,
            w_minute: u16,
            w_second: u16,
            w_milliseconds: u16,
        }
        #[link(name = "kernel32")]
        extern "system" {
            fn GetLocalTime(lp_system_time: *mut Systemtime);
        }

        let mut st = MaybeUninit::<Systemtime>::uninit();
        // GetLocalTime 不会失败，调用后结构体各字段必然有效
        unsafe {
            GetLocalTime(st.as_mut_ptr());
            let st = st.assume_init();
            LocalNow {
                year: u32::from(st.w_year),
                month: u32::from(st.w_month),
                day: u32::from(st.w_day),
                hour: u32::from(st.w_hour),
                minute: u32::from(st.w_minute),
                second: u32::from(st.w_second),
            }
        }
    }
    #[cfg(not(windows))]
    {
        use std::time::{SystemTime, UNIX_EPOCH};
        let secs = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let days = (secs / 86_400) as i64;
        let (y, m, d) = civil_from_days(days);
        let tod = secs % 86_400;
        LocalNow {
            year: y as u32,
            month: m,
            day: d,
            hour: (tod / 3600) as u32,
            minute: ((tod % 3600) / 60) as u32,
            second: (tod % 60) as u32,
        }
    }
}

/// 本地时间 `HH:MM:SS`
pub fn now_time() -> String {
    let n = local_now();
    format!("{:02}:{:02}:{:02}", n.hour, n.minute, n.second)
}

/// Howard Hinnant 的 `days -> (y, m, d)` 算法（days 自 1970-01-01 起）。
/// 仅非 Windows 回退路径使用。
#[cfg(not(windows))]
fn civil_from_days(z: i64) -> (i64, u32, u32) {
    let z = z + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = z - era * 146_097; // [0, 146096]
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365; // [0, 399]
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100); // [0, 365]
    let mp = (5 * doy + 2) / 153; // [0, 11]
    let d = (doy - (153 * mp + 2) / 5 + 1) as u32; // [1, 31]
    let m = if mp < 10 { mp + 3 } else { mp - 9 } as u32; // [1, 12]
    (if m <= 2 { y + 1 } else { y }, m, d)
}

/// 宽松版本号比较：1 = a>b, -1 = a<b, 0 = 相等
pub fn compare_version(a: &str, b: &str) -> i8 {
    match cmp_ver(a, b) {
        Ordering::Greater => 1,
        Ordering::Less => -1,
        Ordering::Equal => 0,
    }
}

fn split_ver(v: &str) -> Vec<String> {
    let trimmed = v.trim();
    let trimmed = trimmed.trim_start_matches(|c: char| !c.is_ascii_digit());
    trimmed
        .split(['.', '-', '+', '_'])
        .filter(|s| !s.is_empty())
        .map(|s| s.to_string())
        .collect()
}

fn cmp_ver(a: &str, b: &str) -> Ordering {
    let va = split_ver(a);
    let vb = split_ver(b);
    let len = va.len().max(vb.len());

    for i in 0..len {
        let x = va.get(i).map(|s| s.as_str()).unwrap_or("0");
        let y = vb.get(i).map(|s| s.as_str()).unwrap_or("0");
        let nx = x.parse::<u64>().ok();
        let ny = y.parse::<u64>().ok();

        match (nx, ny) {
            (Some(p), Some(q)) => {
                if p != q {
                    return p.cmp(&q);
                }
            }
            // 数字段优先于非数字段（release > prerelease）
            (Some(_), None) => return Ordering::Greater,
            (None, Some(_)) => return Ordering::Less,
            (None, None) => {
                if x != y {
                    return x.cmp(y);
                }
            }
        }
    }
    Ordering::Equal
}

/// 从可能带前导警告的输出里抠出 JSON 对象（第一个 { 到最后一个 }）
pub fn extract_json_object(s: &str) -> Option<&str> {
    let start = s.find('{')?;
    let end = s.rfind('}')?;
    if end > start {
        Some(&s[start..=end])
    } else {
        None
    }
}

/// 从可能带前导警告的输出里抠出 JSON 数组（第一个 [ 到最后一个 ]）
pub fn extract_json_array(s: &str) -> Option<&str> {
    let start = s.find('[')?;
    let end = s.rfind(']')?;
    if end > start {
        Some(&s[start..=end])
    } else {
        None
    }
}

/// 受限并发的并行 map。把输入切成 `limit` 份交给工作线程，
/// 用于批量跑外部命令（npm view / pip 查询等网络 IO 密集操作）。
///
/// 优化：工作线程 panic 时输出错误日志，避免静默丢失结果。
pub fn par_map<T, R, F>(items: Vec<T>, limit: usize, f: F) -> Vec<R>
where
    T: Send + Sync + 'static,
    R: Send + 'static,
    F: Fn(&T) -> R + Send + Sync + 'static,
{
    let n = items.len();
    if n == 0 {
        return Vec::new();
    }

    let workers = limit.max(1).min(n);
    let chunk = n.div_ceil(workers);

    let shared = std::sync::Arc::new(items);
    let func = std::sync::Arc::new(f);
    let mut handles = Vec::with_capacity(workers);

    for idx in 0..workers {
        let start = idx * chunk;
        if start >= n {
            break;
        }
        let end = (start + chunk).min(n);
        let shared = shared.clone();
        let func = func.clone();

        handles.push(std::thread::spawn(move || {
            let mut out = Vec::with_capacity(end - start);
            for i in start..end {
                out.push(func(&shared[i]));
            }
            out
        }));
    }

    let mut result = Vec::with_capacity(n);
    for (i, h) in handles.into_iter().enumerate() {
        match h.join() {
            Ok(mut part) => result.append(&mut part),
            Err(_) => eprintln!("[par_map] 工作线程 {} panic，对应结果已丢失", i),
        }
    }
    result
}

/// 当前 PATH 上 node 的版本号（去掉前缀 v），没装返回 None。
pub fn node_version() -> Option<String> {
    let raw = run_capture_with_timeout("node", &["-v"], 10).ok()?;
    let v = raw.trim().trim_start_matches('v').trim().to_string();
    if v.is_empty() {
        None
    } else {
        Some(v)
    }
}

/// 该 Node 版本是否满足 OpenClaw 的要求。
///
/// 区间不是猜的 —— 是 openclaw 自己的 preinstall 守卫打印出来的原文：
/// `this OpenClaw release requires Node >=24.16.0 <25 || >=26.1.0.`
/// （25.x 被明确排除；26.x 要 >= 26.1）
///
/// 放进 app 是为了**提前把话说明白**：让用户在界面上看到「Node 版本过低」，
/// 而不是让 npm 抛一堆守卫报错和 ENOENT，完全看不出问题在哪。
pub fn node_satisfies_openclaw(v: &str) -> bool {
    let mut it = v.split('.').filter_map(|p| p.parse::<u32>().ok());
    let (Some(major), Some(minor)) = (it.next(), it.next()) else {
        return false;
    };
    match major {
        24 => minor >= 16,
        26 => minor >= 1,
        m if m >= 27 => true,
        _ => false, // <24 与 25.x 都不满足
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 回归测试：退出码必须能被调用方拿到。
    ///
    /// 修复前 `run_capture*` 只回传 stdout，退出码彻底丢失，于是三个更新源
    /// 无法区分"没有可更新项"与"检查失败"——后者会解析不出 JSON、打绿色 OK
    /// 谎报"全部是最新版本"。这个用例锁死"非 0 退出码不再被吞掉"。
    #[test]
    fn capture_status_reports_nonzero_exit() {
        // Windows 上用 cmd 制造一个确定的失败退出码
        let (code, text) = run_capture_status("cmd", &["/C", "exit 3"], 15)
            .expect("cmd 应该能执行");
        assert_eq!(code, 3, "非 0 退出码必须原样回传，而不是被丢弃");
        // 失败时 stdout 为空，应回退到 stderr 或空串，但不能 panic
        let _ = text;
    }

    #[test]
    fn capture_status_reports_zero_exit() {
        let (code, text) = run_capture_status("cmd", &["/C", "echo", "hello"], 15)
            .expect("cmd 应该能执行");
        assert_eq!(code, 0, "成功时退出码应为 0");
        assert!(
            text.contains("hello"),
            "成功时 stdout 必须被捕获到，实际: {:?}",
            text
        );
    }

    /// OpenClaw 的 Node 版本要求：>=24.16 <25 || >=26.1（25.x 明确排除）
    #[test]
    fn node_requirement_matches_openclaw_guard() {
        for (v, ok) in [
            ("22.22.2", false),
            ("24.15.9", false),
            ("24.16.0", true),
            ("25.0.0", false), // 要求里明确排除 25.x
            ("25.9.9", false),
            ("26.0.0", false), // 26 要 >= 26.1
            ("26.1.0", true),
            ("26.4.0", true),
            ("27.0.0", true),
            ("", false),
            ("not-a-version", false),
        ] {
            assert_eq!(node_satisfies_openclaw(v), ok, "版本 {} 判定错误", v);
        }
    }
}
