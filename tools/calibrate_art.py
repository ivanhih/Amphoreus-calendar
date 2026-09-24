"""量出 12 张月份海报的版式边界，产出 Art.java 的逐月常量与 docs/art-bounds.json。

用法：
    python tools/calibrate_art.py                 # 量一遍，打印表格并写 JSON
    python tools/calibrate_art.py --check         # 只校验，与期望区间不一致就非零退出
    python tools/calibrate_art.py --write-java    # 把结果写回 Art.java 的 CALIBRATED 标记块

为什么需要它：首页「上半是插画、下半是真实日期格」的融合版式，必须知道每张海报的
插画区底边在哪（0.585 那条线）以及插画底边是什么颜色（用来做接缝，让上下像同一张纸）。
这两个数从图里量出来，而不是靠肉眼估一个常数——原来写死 0.59 的单一常数对 12 张图
都偏，且无法复核。

量法：把海报按行求「水平梯度能量」（一行左右相邻像素差分的平均绝对值），插画画面上
到处都是细节，梯度高；插画与印刷日期表之间的标题带、以及表格内部的空白行梯度低。
再对这条曲线做低通 + 局部极大值，就得到各条分界。

一个刻意留下的空白：海报下方那张印刷日期表的左右内边距**量不出来**（它的底色是一条
从暗到亮的横向渐变，没有贯穿整表的竖线，任何阈值法都把背景当成了内容）。所以应用下半
的左右内边距是我们自己定的 16dp，不是照抄量到的值——文档里不要写成「复刻了印刷表的
边距」。印刷表真正被我们参考的是它的语言：无圆角、竖分隔线、无横线、小号衬线数字。
"""
import argparse
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# 项目根目录：本脚本固定在 tools/ 下，所以取它的上一级。脚本里所有文件访问都经由
# project_path() 构造，那个函数会拒绝任何越出项目目录的路径。
PROJECT_ROOT = Path(__file__).resolve().parent.parent

# 海报原图尺寸，与 Art.java 的 SOURCE_WIDTH/SOURCE_HEIGHT 必须一致。
SOURCE_WIDTH = 1125
SOURCE_HEIGHT = 2436

ART_JAVA_PARTS = ("app", "src", "main", "java", "com", "amphoreus", "calendar", "ui", "Art.java")

# 各分界的搜索窗口（占原图高度比例）与期望区间。期望值是 2026-09-10 实测 12 张得到的，
# 换素材时这里会失败——那正是它的用处：提醒重新确认版式，而不是静默漂移。
# 插画顶边不在这里：海报最上面就是画面，量不出稳定的分界（各月"首个峰"落在 0.035-0.197 之间
# 且互不相关，是画面内容不是版式线），所以固定按 0 处理。
SEARCH = {
    "artwork_bottom": (0.54, 0.62),
    "table_header_top": (0.70, 0.755),
    "barcode_top": (0.915, 0.965),
}
EXPECTED = {
    "artwork_bottom": (0.555, 0.615),
    "table_header_top": (0.700, 0.760),
    "barcode_top": (0.910, 0.970),
}
# 12 张图的插画底边必须落在这么窄的一条带里：单看一个月看不出问题，12 个月一起才说明
# "一个常数够用"，否则首页就得逐月换裁切线（那会让版式很难维护）。
ARTWORK_BOTTOM_SPREAD = 0.02

# 主体质心的合理范围（占插画区段的比例）。2026-09-11 实测 12 张：纵向 0.533-0.576、
# 横向 0.490-0.577。质心不是"主体边界"，是细节质量的加权中心——桌面卡片裁切窗口以它为
# 中心放置，实测比"窗口最优摆放"平均只差 2.1% 的保留质量，但只需要每月两个数。
FOCUS_RANGE = (0.30, 0.70)
FOCUS_SPREAD = 0.15


def project_path(*parts):
    """项目内某个路径。

    只接受相对项目根目录、且不含 .. 的普通路径片段；解析后必须仍落在项目内，否则拒绝。
    这样脚本里不存在「路径穿越」这种可能，也不依赖调用者的当前工作目录。
    """
    for part in parts:
        candidate = Path(part)
        if not part or candidate.is_absolute() or ".." in candidate.parts:
            raise SystemExit("拒绝不安全的路径片段: %r" % (part,))
    resolved = PROJECT_ROOT.joinpath(*parts).resolve()
    if resolved != PROJECT_ROOT and PROJECT_ROOT not in resolved.parents:
        raise SystemExit("拒绝访问项目目录之外的路径: %s" % resolved)
    return resolved


def art_file(month):
    """某个月的海报素材路径；月份只接受 0-12，拼进文件名前先过范围检查。"""
    if not isinstance(month, int) or not 0 <= month <= 12:
        raise SystemExit("月份越界: %r" % (month,))
    return project_path("app", "src", "main", "assets", "art", "month_%02d.jpg" % month)


def profile(gray):
    """每行的水平梯度能量，低通后归一化到 0..1，便于跨图比较。"""
    energy = np.abs(np.diff(gray, axis=1)).mean(axis=1)
    smoothed = np.convolve(energy, np.ones(9) / 9.0, "same")
    span = smoothed.max() - smoothed.min()
    return (smoothed - smoothed.min()) / (span + 1e-6)


def find_edge(curve, window, merge=0.014, pair_ratio=0.3):
    """在比例窗口内找插画区底边，返回它的比例；找不到任何峰则返回 None。

    版式是这样：插画底部与标题带之间有一组**双线**（实测稳定在约 0.585 与 0.607），
    两条线的相对强度逐月互换——9 月前一条更强（14.7 对 10.1），6 月后一条更强
    （8.1 对 17.5）。插画能显示到的边界是前一条，所以判据写成"先找最强的那条线，
    再取它前面那条线；前面那条够强就用它"。

    两个必要的细节，缺一个都会得到"12 个月分成两组"的假象：
    - 先把相距小于 merge 的极大值并成同一条线（曲线上到处是细碎噪声峰，不合并的话
      "前面那条线"会命中噪声而不是真正的双线同伴）；
    - "够强"用相对最强线的比例判定，而不是绝对阈值或 mean+std：6 月的前一条线只有
      后一条的一半强度，任何绝对判据都会把它当成噪声跳过。
    """
    height = len(curve)
    lo, hi = int(height * window[0]), int(height * window[1])
    segment = curve[lo:hi]
    if segment.size == 0:
        return None
    maxima = [index for index in range(1, len(segment) - 1)
              if segment[index] >= segment[index - 1] and segment[index] > segment[index + 1]]
    if not maxima:
        return None
    # 合并成互不重叠的"线"：每条线记它最强的那个位置。
    lines = []
    for index in sorted(maxima):
        if lines and index - lines[-1] <= int(height * merge):
            if segment[index] > segment[lines[-1]]:
                lines[-1] = index
        else:
            lines.append(index)
    strong = max(lines, key=lambda index: segment[index])
    previous = [index for index in lines if index < strong]
    if previous and segment[previous[-1]] >= pair_ratio * segment[strong]:
        strong = previous[-1]
    return (lo + strong) / float(height)


def edge_color(rgb, ratio, thickness=12):
    """插画底边往上若干行的平均色：接缝要接的是插画最后一行，不是它下面的标题带。"""
    row = int(SOURCE_HEIGHT * ratio)
    band = rgb[max(0, row - thickness):max(1, row)]
    if band.size == 0:
        return (0x10, 0x11, 0x1c)
    mean = band.reshape(-1, 3).mean(axis=0)
    return tuple(int(round(channel)) for channel in mean)


def focus_point(rgb, gray, bottom):
    """插画区段内的细节质心 (x, y)，都是占区段的比例。

    细节量 = 0.6*行内水平梯度 + 0.4*行内饱和度（各自归一化）。角色/前景处细节多、
    纯背景处少，所以质量中心落在主体上。窗口对准质心放置，与"逐窗口找最优摆放"相比
    实测平均只差 2.1% 的保留质量（2026-09-11，12 张，v=0.69/0.43 两档），但只需要
    每月两个数。刻意不用"95% 质量分位区间"：实测 12 张的 2.5%/97.5% 分位都落在
    0.05/0.98 附近——顶部只是细节较少而不是空白，分位数法区分不出主体。
    """
    rows = int(gray.shape[0] * bottom)
    art = gray[:rows]
    art_rgb = rgb[:rows]
    edge = np.abs(np.diff(art, axis=1)).mean(axis=1)
    saturation = (art_rgb.max(axis=2) - art_rgb.min(axis=2)).mean(axis=1)
    detail = 0.6 * edge / max(edge.max(), 1e-6) + 0.4 * saturation / max(saturation.max(), 1e-6)
    row_mass = detail / detail.sum()
    column_mass = np.abs(np.diff(art, axis=0)).mean(axis=0)
    center_y = float((row_mass * np.arange(rows)).sum() / rows)
    center_x = float((column_mass / column_mass.sum() * np.arange(art.shape[1])).sum() / art.shape[1])
    return center_x, center_y


def measure(month):
    # 一张图只解码一次，转灰度另取一份，避免重复读盘。
    with Image.open(art_file(month)) as handle:
        rgb = np.asarray(handle.convert("RGB")).astype(np.float32)
        gray = np.asarray(handle.convert("L")).astype(np.float32)
    curve = profile(gray)
    bounds = {}
    for name, window in SEARCH.items():
        bounds[name] = find_edge(curve, window)
    bottom = bounds.get("artwork_bottom")
    if bottom is None:
        raise SystemExit("month_%02d: 找不到插画区底边，素材版式可能变了" % month)
    # 印刷日期表的下边界：取条码带上方的最后一个显著峰。
    tail = find_edge(curve, (0.86, 0.940))
    bottom_color = edge_color(rgb, bottom)
    focus_x, focus_y = focus_point(rgb, gray, bottom)
    return {
        "artwork_top": 0.0,
        "artwork_bottom": round(bottom, 4),
        "artwork_aspect": round(SOURCE_WIDTH / (SOURCE_HEIGHT * bottom), 4),
        "edge_color": "#%02x%02x%02x" % bottom_color,
        "edge_color_rgb": list(bottom_color),
        "focus_x": round(focus_x, 4),
        "focus_y": round(focus_y, 4),
        "table_header_top": None if bounds.get("table_header_top") is None else round(bounds["table_header_top"], 4),
        "table_bottom": None if tail is None else round(tail, 4),
        "barcode_top": None if bounds.get("barcode_top") is None else round(bounds["barcode_top"], 4),
    }


def collect(months):
    return {str(month): measure(month) for month in months}


def check(data):
    """校验量出的边界是否落在期望区间，并检查逐月取值的一致性。"""
    problems = []
    for month, values in sorted(data.items(), key=lambda item: int(item[0])):
        for name, (low, high) in EXPECTED.items():
            value = values.get(name)
            if value is None:
                problems.append("month_%s: 没量到 %s" % (month.zfill(2), name))
            elif not (low <= value <= high):
                problems.append("month_%s: %s=%.4f 不在 [%.3f, %.3f]" % (month.zfill(2), name, value, low, high))
        aspect = values.get("artwork_aspect")
        if aspect is None or not (0.70 <= aspect <= 0.90):
            problems.append("month_%s: 插画区宽高比 %.4f 不合理（应在 0.70-0.90）" % (month.zfill(2), aspect or 0))
    colors = {values.get("edge_color") for values in data.values()}
    if len(colors) < len(data):
        problems.append("插画底边色只有 %d 种（共 %d 个月），逐月主题色会撞车" % (len(colors), len(data)))
    bottoms = [values["artwork_bottom"] for values in data.values() if values.get("artwork_bottom") is not None]
    if len(bottoms) > 1:
        spread = max(bottoms) - min(bottoms)
        if spread > ARTWORK_BOTTOM_SPREAD:
            problems.append("插画底边在 12 个月里相差 %.4f（上限 %.2f）：%.4f-%.4f，"
                            "单个常数不再适用，需要逐月裁切线"
                            % (spread, ARTWORK_BOTTOM_SPREAD, min(bottoms), max(bottoms)))
    for axis in ("focus_x", "focus_y"):
        values_found = [values.get(axis) for values in data.values()]
        if any(value is None for value in values_found):
            problems.append("有月份没量到 %s" % axis)
            continue
        low, high = FOCUS_RANGE
        for month, value in sorted(data.items(), key=lambda item: int(item[0])):
            if not low <= value[axis] <= high:
                problems.append("month_%s: %s=%.4f 不在 [%.2f, %.2f]，质心跑到边缘通常说明量到了边框或文字"
                                % (month.zfill(2), axis, value[axis], low, high))
        spread = max(values_found) - min(values_found)
        if spread > FOCUS_SPREAD:
            problems.append("%s 在 12 个月里相差 %.4f（上限 %.2f）：窗口策略可能对某些月失效"
                            % (axis, spread, FOCUS_SPREAD))
    return problems


def java_block(data):
    """生成 Art.java 里被标记块包裹的常量，索引 0 是年历封面（沿用 1 月的值）。"""
    months = sorted(data, key=int)
    bottoms = ["%.4ff" % data[str(month)]["artwork_bottom"] for month in months]
    colors = [tuple(data[str(month)]["edge_color_rgb"]) for month in months]
    focus_x = ["%.4ff" % data[str(month)]["focus_x"] for month in months]
    focus_y = ["%.4ff" % data[str(month)]["focus_y"] for month in months]
    lines = [
        "    // BEGIN CALIBRATED (tools/calibrate_art.py --write-java)",
        "    /** 插画区底边占原图高度的比例，逐月量出。索引 0 是年历封面。 */",
        "    private static final float[] ARTWORK_BOTTOM={%s};" % ",".join(["0.585f"] + bottoms),
        "    /** 插画底边往上 12 行的平均色，用作插画与日期格之间的接缝色。索引 0 是年历封面。 */",
        "    private static final int[] EDGE_COLORS={%s};" % ",".join(
            ["0xff%02x%02x%02x" % colors[0]] + ["0xff%02x%02x%02x" % color for color in colors]),
        "    /** 插画区段内细节质心的位置（x/y 各占区段宽高的比例）。桌面卡片的裁切窗口以它为中心放置。",
        "     *  逐月量出；索引 0 是年历封面。 */",
        "    private static final float[] FOCUS_X={%s};" % ",".join([focus_x[0]] + focus_x),
        "    private static final float[] FOCUS_Y={%s};" % ",".join([focus_y[0]] + focus_y),
        "    // END CALIBRATED",
    ]
    return "\n".join(lines)


def write_java(data):
    """把常量写回 Art.java 的标记块，返回是否有改动。"""
    pattern = re.compile(r"[ \t]*// BEGIN CALIBRATED.*?// END CALIBRATED\n", re.S)
    target = project_path(*ART_JAVA_PARTS)
    source = target.read_text(encoding="utf-8")
    if not pattern.search(source):
        raise SystemExit("Art.java 里找不到 CALIBRATED 标记块，无法写入")
    updated = pattern.sub(java_block(data) + "\n", source, count=1)
    # 显式 newline="\n"：Windows 上默认会把换行翻成 CRLF，那样每次运行都会产生无意义的整文件差异。
    with target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(updated)
    return updated != source


def write_report(data):
    """写 docs/art-bounds.json：量出来的数据连同方法与已知局限。"""
    project_path("docs").mkdir(parents=True, exist_ok=True)
    with project_path("docs", "art-bounds.json").open("w", encoding="utf-8", newline="\n") as handle:
        json.dump({
            "source": "%dx%d" % (SOURCE_WIDTH, SOURCE_HEIGHT),
            "method": "逐行水平梯度能量的局部极大值；插画底边色取底边往上 12 行的平均",
            "note": "印刷日期表的左右内边距无法可靠测量（背景是横向亮度渐变、无贯穿竖线），"
                    "应用下半的 16dp 内边距是设计选择而非照抄",
            "months": data,
        }, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")


def main():
    parser = argparse.ArgumentParser(description="量出月份海报的版式边界")
    parser.add_argument("--check", action="store_true", help="只校验，不写文件")
    parser.add_argument("--write-java", action="store_true", help="把常量写回 Art.java 的标记块")
    parser.add_argument("--months", default="1-12", help="要量的月份，如 1-12 或 1,4,9")
    arguments = parser.parse_args()

    if "-" in arguments.months:
        low, high = arguments.months.split("-")
        months = list(range(int(low), int(high) + 1))
    else:
        months = [int(part) for part in arguments.months.split(",")]

    data = collect(months)
    print("%-8s %8s %8s %8s %10s %10s  %s" % ("月份", "插画底", "宽高比", "条码带", "质心x", "质心y", "插画底边色"))
    for month in months:
        values = data[str(month)]
        barcode = "n/a" if values["barcode_top"] is None else "%.3f" % values["barcode_top"]
        print("%-8s %8.3f %8.3f %8s %10.3f %10.3f  %s" % (
            "month_%02d" % month, values["artwork_bottom"], values["artwork_aspect"], barcode,
            values["focus_x"], values["focus_y"],
            values["edge_color"]))

    problems = check(data)
    if arguments.check:
        for problem in problems:
            print("FAIL " + problem)
        print("结论: " + ("PASS" if not problems else "FAIL"))
        return 1 if problems else 0

    write_report(data)
    print("已写入 docs/art-bounds.json")
    if arguments.write_java:
        print("Art.java " + ("已更新" if write_java(data) else "无变化"))
    for problem in problems:
        print("WARN " + problem)
    return 0


if __name__ == "__main__":
    sys.exit(main())
