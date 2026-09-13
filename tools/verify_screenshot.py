"""独立核对实机截图：融合版式是否真的成立（插画完整、日期格是印刷语言、主题色落对位置）。

用法：
    python tools/verify_screenshot.py --shot <截图.png> --tree <uiautomator.xml> --month 2026-09 [--report docs/xxx.txt]

为什么重写：旧版把 1080x2400 @420 下的像素框写死在代码里，首页版式一改就整片失效，
也没法在别的分辨率上跑。现在区域从 `tools/view_tree.py` 解析的视图树里取，于是
1080x1920 / 1440x3120 / 平板可以跑同一份核对。

核对的是"这次改版到底做成了没有"，所以断言都对着用户看得见的东西：
1. 插画面板真的是当月那张插画——拿面板区域与 `month_XX.jpg` 的插画区段做归一化互相关；
2. 插画没有被渐隐蒙版洗掉——面板下 20% 的亮度不能明显低于原图（旧版那层 45% 高的黑罩
   会在这里直接失败）；
3. 插画与日期格之间是连续的——接缝两侧的平均色差很小；
4. 日期格是印刷语言——没有圆角、竖分隔线落在列边界上；
5. 整月在首屏内，且日期是应用算出来的真实公历，不是海报印刷的 2026 排布；
6. 颜色都对——选中格是主题色实心、日期区没有残留的旧金色、正文对比度达标。

OCR 只作为旁证：深色渲染下 tesseract 命中率低，日期正确性主要靠视图树的
content description 与真实公历比对，这一点没变。
"""
import argparse
import collections
import json
import os
import re
import subprocess
import sys
from datetime import date
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import view_tree

PROJECT_ROOT = Path(__file__).resolve().parent.parent

ACCENTS = {
    1: (0xD4, 0x9E, 0x91), 2: (0x80, 0xA9, 0xE5), 3: (0xC9, 0xA2, 0xE0), 4: (0xD3, 0xC4, 0x92),
    5: (0xA5, 0x8C, 0xEE), 6: (0xEE, 0x96, 0xB2), 7: (0x87, 0xAF, 0xE1), 8: (0x90, 0xD4, 0xC6),
    9: (0xD7, 0xBE, 0x8E), 10: (0xE6, 0x9E, 0x7F), 11: (0xCD, 0x98, 0xE8), 12: (0xB8, 0x98, 0xE8),
}
GOLD = (0xE6, 0xC8, 0x8C)
TEXT = (0xF4, 0xF0, 0xE7)
MUTED = (0xB2, 0xB0, 0xC1)
BG = (0x10, 0x11, 0x1C)
DIM = (0x62, 0x61, 0x73)
# 判据定得比实测松一点，留出 JPEG 编码与缩放的余量。
MIN_ART_CORRELATION = 0.93
MIN_ART_SHARE = 0.40          # 插画面板至少要占屏高的比例
MIN_VISIBLE_SHARE = 0.60      # 插画本身至少要有这么多高度被显示出来（零裁切时是 1.0）
MIN_BRIGHTNESS_KEEP = 0.65    # 面板下 20% 的亮度相对原图的保留比例
MAX_SEAM_JUMP = 12.0          # 接缝两侧平均色的最大差值（0-255）


def near(color, target, tol=12):
    return all(abs(int(color[index]) - target[index]) <= tol for index in range(3))


def month_length(year, month):
    return 31 if month == 12 else (date(year, month + 1, 1) - date(year, month, 1)).days


def grid_start(year, month, monday_first=True):
    first = date(year, month, 1)
    offset = first.weekday() if monday_first else (first.weekday() + 1) % 7
    return date.fromordinal(first.toordinal() - offset)


def expected_cells(year, month, monday_first=True):
    """应用应当渲染的 42 格窗口，以及本月实际要画的行数。"""
    start = grid_start(year, month, monday_first)
    offset = (date(year, month, 1) - start).days
    rows = min(6, -(-(offset + month_length(year, month)) // 7))
    return [date.fromordinal(start.toordinal() + index) for index in range(42)], rows


def relative_luminance(color):
    def channel(value):
        value = value / 255.0
        return value / 12.92 if value <= 0.03928 else ((value + 0.055) / 1.055) ** 2.4
    return 0.2126 * channel(color[0]) + 0.7152 * channel(color[1]) + 0.0722 * channel(color[2])


def contrast(first, second):
    a, b = relative_luminance(first), relative_luminance(second)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


def normalised_correlation(left, right):
    left = left - left.mean()
    right = right - right.mean()
    denominator = np.sqrt((left * left).sum()) * np.sqrt((right * right).sum())
    return float((left * right).sum() / (denominator + 1e-9))


def artwork_region(month):
    """当月海报的插画区段：底边比例读 docs/art-bounds.json，与 Art.java 的常量同源。"""
    with (PROJECT_ROOT / "docs" / "art-bounds.json").open(encoding="utf-8") as handle:
        bottom = json.load(handle)["months"][str(month)]["artwork_bottom"]
    with Image.open(PROJECT_ROOT / "app" / "src" / "main" / "assets" / "art" / ("month_%02d.jpg" % month)) as handle:
        source = handle.convert("RGB")
        return source.crop((0, 0, source.width, int(source.height * bottom)))


def best_correlation(panel, source, steps=25):
    """面板与插画区段的归一化互相关，返回 (最大相关系数, 可见比例, 最佳取景偏移)。

    面板的宽高比与插画区段不一致时（屏幕太矮，不得不裁），取景窗口落在哪一段取决于
    Art 里的 CROP_BIAS。这里不假设那个常量，而是在纵向上滑一遍取最大值——测的是
    "面板里确实是这张插画"，不是"裁切公式没变"。
    """
    width, height = panel.size
    aspect = width / float(height)
    # 面板比插画"更宽"时（矮屏、插画被裁），取景窗口只占插画的一部分高度；面板与插画同比例时
    # 窗口就是整个插画区段（零裁切）。这里一度把两种情况写反了：1080x1920 下拿整幅竖版插画去
    # 比一个横条面板，互相关掉到 0.77，看着像应用出了问题，其实是核对自己的取景算错了。
    window = min(source.height, int(round(source.width / aspect)))
    span = source.height - window
    actual = np.asarray(panel.resize((180, 200)).convert("L"), dtype=np.float32)

    def score(offset):
        crop = source.crop((0, offset, source.width, offset + window))
        reference = np.asarray(crop.resize((180, 200)).convert("L"), dtype=np.float32)
        return normalised_correlation(actual, reference)

    # 先粗扫找到大致取景位置，再在邻域逐像素收敛。只粗扫的话步长未必落在应用真正用的取景
    # 偏移上（它按 CROP_BIAS 算，可能是任意值），参考裁剪会差十几行，相关系数白掉几分，
    # 短屏上就会假报失败。
    coarse = sorted({0, span} | {int(round(span * step / max(1, steps - 1))) for step in range(steps)})
    best_offset = max(coarse, key=score)
    step = max(1, span // max(1, steps - 1))
    for offset in range(max(0, best_offset - step), min(span, best_offset + step) + 1):
        if score(offset) > score(best_offset):
            best_offset = offset
    return score(best_offset), window / float(source.height), best_offset


def sheet_colour(image, box):
    pixels = np.asarray(image.crop(box).convert("RGB")).reshape(-1, 3)
    return collections.Counter(map(tuple, pixels)).most_common(1)[0][0]


def detect_rules(image, band, sheet, skip=None, threshold=0.7):
    """在一条日期行里找出贯穿整格高度的竖分隔线，返回它们的 x 坐标。"""
    x0, y0, x1, y1 = band
    region = np.asarray(image.crop((x0, y0, x1, y1)).convert("RGB"), dtype=np.int16)
    distance = np.abs(region - np.array(sheet, dtype=np.int16)).max(axis=2)
    fraction = (distance > 15).mean(axis=0)
    rules = []
    for index in range(region.shape[1]):
        absolute = x0 + index
        if skip is not None and skip[0] - 2 <= absolute <= skip[2] + 2:
            continue          # 选中格是主题色实心块，整列都会被判成"非底色"，必须排除
        if fraction[index] >= threshold:
            rules.append(absolute)
    merged = []
    for position in rules:
        if merged and position - merged[-1][-1] <= 2:
            merged[-1].append(position)
        else:
            merged.append([position])
    return [sum(group) / len(group) for group in merged]


def ocr_digits(path):
    """返回识别出的整数列表（二值化后交给系统 tesseract）。识别不出来就返回空列表。"""
    temporary = str(path) + ".ocr.png"
    try:
        Image.open(path).convert("L").point(lambda value: 0 if value < 110 else 255).save(temporary)
        out = subprocess.run(
            ["tesseract", temporary, "stdout", "--psm", "6", "-c", "tessedit_char_whitelist=0123456789"],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
    except (OSError, subprocess.SubprocessError):
        return []
    finally:
        try:
            os.remove(temporary)
        except OSError:
            pass
    return [int(value) for value in re.findall(r"\d+", out.stdout or "")]


def report_path(candidate):
    """报告只能写到项目目录内，避免这个脚本被当成往任意位置写文件的工具。"""
    resolved = Path(candidate).resolve()
    if resolved != PROJECT_ROOT and PROJECT_ROOT not in resolved.parents:
        raise SystemExit("拒绝写到项目目录之外: %s" % resolved)
    return resolved


def series(directory, year, report):
    """跨 12 个月核对：每个月是否真的用了自己那套主题色。

    这条检查替代的是"人眼看一遍 12 张拼图"：拼图能让眼睛比较逐月配色，但没有视觉模型可用时
    （本机实测评审子代理读不到像素），至少要能自动断言 12 个月确实各不相同、且与 Ui.ACCENTS
    一致。取色位置用视图树里的年月标题——它就是用当月主题色画的。
    """
    lines, failures = [], []
    roots = Path(directory)
    seen = {}
    for month in range(1, 13):
        stamp = "%s-%02d" % (year, month)
        shot = roots / ("fusion-%s.png" % stamp)
        tree_file = PROJECT_ROOT / "build" / "fusion" / ("ui-device-%s.xml" % stamp)
        if not shot.is_file() or not tree_file.is_file():
            failures.append("%s 缺少截图或视图树" % stamp)
            continue
        tree = view_tree.load(tree_file)
        if not tree["title"]:
            failures.append("%s 视图树里没有年月标题" % stamp)
            continue
        image = Image.open(shot).convert("RGB")
        pixels = np.asarray(image.crop(tuple(tree["title"])).convert("RGB")).reshape(-1, 3)
        # 标题是浅色文字压在深色底上：丢掉接近底色的像素，剩下的就是主题色。
        lit = [tuple(int(v) for v in pixel) for pixel in pixels if int(pixel.sum()) > 300]
        if not lit:
            failures.append("%s 标题区域没有可测的着色像素" % stamp)
            continue
        measured = collections.Counter(lit).most_common(1)[0][0]
        expected = ACCENTS[month]
        match = near(measured, expected, 26)
        seen[month] = measured
        lines.append("%s 标题实测 #%02x%02x%02x，期望 #%02x%02x%02x %s"
                     % (stamp, measured[0], measured[1], measured[2], expected[0], expected[1], expected[2],
                        "[OK]" if match else "[不符]"))
        if not match:
            failures.append("%s 的年月标题不是当月主题色" % stamp)
    distinct = len({value for value in seen.values()})
    lines.append("12 个月共出现 %d 种不同的标题色" % distinct)
    if len(seen) == 12 and distinct < 12:
        failures.append("12 个月的标题色只出现 %d 种，逐月主题色没有生效" % distinct)
    lines.append("结论: " + ("PASS" if not failures else "FAIL -> " + "; ".join(failures)))
    if report:
        target = report_path(report)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("\n".join(lines) + "\n")
    print("verify series: %s (%d months, %d distinct)" % ("PASS" if not failures else "FAIL", len(seen), distinct))
    for failure in failures:
        print("  - " + failure.encode("ascii", "replace").decode("ascii"))
    return 0 if not failures else 1


def main():
    parser = argparse.ArgumentParser(description="核对融合版式首页的实机截图")
    parser.add_argument("--shot", help="截图 PNG")
    parser.add_argument("--tree", help="uiautomator dump 出来的 XML")
    parser.add_argument("--month", help="年月，如 2026-09")
    parser.add_argument("--report", help="报告写到项目目录内的某个文件")
    parser.add_argument("--sunday-first", action="store_true", help="设置里改成周日开始时用")
    parser.add_argument("--series", help="改为跨月核对：传 docs/screenshots 所在目录")
    parser.add_argument("--year", default="2026", help="与 --series 搭配的年份")
    arguments = parser.parse_args()

    if arguments.series:
        return series(arguments.series, arguments.year, arguments.report)
    for required in ("shot", "tree", "month"):
        if not getattr(arguments, required):
            parser.error("--%s is required" % required)

    year, month = (int(part) for part in arguments.month.split("-"))
    accent = ACCENTS[month]
    image = Image.open(Path(arguments.shot).resolve()).convert("RGB")
    tree = view_tree.load(arguments.tree)
    screen_width, screen_height = tree["screen"]["width"], tree["screen"]["height"]

    lines, failures = [], []

    def check(condition, message):
        if not condition:
            failures.append(message)
        lines.append(("PASS " if condition else "FAIL ") + message)

    lines.append("截图: %s (%dx%d)  视图树: %s  期望主题色: #%02x%02x%02x（%d 月）"
                 % (arguments.shot, screen_width, screen_height, arguments.tree, accent[0], accent[1], accent[2], month))

    # ---- 区域是否齐备 ----
    for name, label in (("art", "插画面板"), ("grid", "日期格"), ("weekday", "星期行")):
        check(tree[name] is not None, "视图树里能定位到%s" % label)
    if failures:
        for line in lines:
            print(line)
        return 1

    art_box, grid_box = tree["art"], tree["grid"]

    # ---- 1. 插画面板：全出血、够高、真的是这张插画 ----
    art_width, art_height = art_box[2] - art_box[0], art_box[3] - art_box[1]
    check(art_width >= screen_width * 0.9, "插画面板全出血（宽 %d / 屏宽 %d）" % (art_width, screen_width))
    share = art_height / float(screen_height)
    check(share >= MIN_ART_SHARE, "插画面板占屏高 %.0f%%（下限 %.0f%%）" % (share * 100, MIN_ART_SHARE * 100))

    source = artwork_region(month)
    panel = image.crop(tuple(art_box))
    correlation, visible, offset = best_correlation(panel, source)
    check(correlation >= MIN_ART_CORRELATION,
          "插画面板与 month_%02d.jpg 的插画区段一致（互相关 %.4f，下限 %.2f）" % (month, correlation, MIN_ART_CORRELATION))
    check(visible >= MIN_VISIBLE_SHARE,
          "插画显示了自身高度的 %.0f%%（下限 %.0f%%；零裁切时是 100%%）" % (visible * 100, MIN_VISIBLE_SHARE * 100))

    # ---- 2. 没有被渐隐蒙版洗掉（旧版那层 45% 高的黑罩会在这里失败）----
    window = max(1, int(round(source.height * visible)))
    reference = source.crop((0, offset, source.width, offset + window)).resize(panel.size)
    band = max(1, art_height // 5)
    keep = np.asarray(panel.convert("L"), dtype=np.float32)[-band:].mean() / max(
        1.0, np.asarray(reference.convert("L"), dtype=np.float32)[-band:].mean())
    check(keep >= MIN_BRIGHTNESS_KEEP,
          "面板下 20%% 的亮度保留了原图的 %.0f%%（下限 %.0f%%，低于它说明又盖上蒙版了）" % (keep * 100, MIN_BRIGHTNESS_KEEP * 100))

    # ---- 3. 接缝连续 ----
    above = np.asarray(image.crop((art_box[0], art_box[3] - 6, art_box[2], art_box[3])).convert("L"), dtype=np.float32).mean()
    below = np.asarray(image.crop((art_box[0], art_box[3], art_box[2], art_box[3] + 6)).convert("L"), dtype=np.float32).mean()
    check(abs(above - below) <= MAX_SEAM_JUMP,
          "插画底部与日期区顶部颜色连续（色差 %.1f，上限 %.0f）" % (abs(above - below), MAX_SEAM_JUMP))

    # ---- 4. 整月在首屏内，且不被底部导航压住 ----
    nav_top = min(box[1] for box in tree["tabs"].values()) if tree["tabs"] else screen_height
    check(grid_box[3] <= screen_height, "日期格末行在屏幕内（%d ≤ %d）" % (grid_box[3], screen_height))
    check(grid_box[3] <= nav_top, "日期格末行在底部导航之上（%d ≤ %d）" % (grid_box[3], nav_top))

    # ---- 5. 日期是应用算出来的真实公历 ----
    cells, rows = expected_cells(year, month, not arguments.sunday_first)
    grouped = view_tree.rows(tree["cells"])
    check(len(tree["cells"]) == rows * 7, "日期格数量 = %d 行 × 7 = %d（实际 %d）" % (rows, rows * 7, len(tree["cells"])))
    check(all(len(group) == 7 for group in grouped), "每一行都是 7 列")
    if tree["cells"]:
        first_seen, last_seen = tree["cells"][0], tree["cells"][-1]
        check((first_seen["month"], first_seen["day"]) == (cells[0].month, cells[0].day),
              "首格是 %d 月 %d 日（应用算出来的窗口起点）" % (cells[0].month, cells[0].day))
        check((last_seen["month"], last_seen["day"]) == (cells[rows * 7 - 1].month, cells[rows * 7 - 1].day),
              "末格是 %d 月 %d 日" % (cells[rows * 7 - 1].month, cells[rows * 7 - 1].day))
        check(any((cell["month"], cell["day"]) == (month, 1) for cell in tree["cells"]), "本月 1 日在网格里")
        check(any((cell["month"], cell["day"]) == (month, month_length(year, month)) for cell in tree["cells"]),
              "本月最后一天在网格里")
        check(len([cell for cell in tree["cells"] if cell["today"]]) <= 1, "最多一个「今天」标记")

    # ---- 6. 日期格是印刷语言：底色带当月色、无圆角、竖线落在列边界 ----
    sheet = sheet_colour(image, tuple(grid_box))
    check(not near(sheet, BG, 3), "日期区底色混入了当月插画底边色（实测 #%02x%02x%02x，不是纯 #10111c）" % sheet)

    selected = None
    for cell in tree["cells"]:
        pixels = np.asarray(image.crop(tuple(cell["bounds"])).convert("RGB")).reshape(-1, 3)
        if sum(1 for pixel in pixels if near(pixel, accent, 14)) > len(pixels) * 0.6:
            selected = cell
            break
    check(selected is not None, "有一个日期格被主题色实心填充（选中态）")
    if selected is not None:
        x0, y0, x1, y1 = selected["bounds"]
        sharp = all(near(image.getpixel(corner), accent, 18)
                    for corner in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)))
        check(sharp, "选中格是直角填充（四角都是主题色；圆角会在这里失败）")

    aligned, rule_total = True, 0
    for index, group in enumerate(grouped):
        boundaries = [cell["bounds"][0] for cell in group] + [group[-1]["bounds"][2]]
        band = (group[0]["bounds"][0], group[0]["bounds"][1], group[-1]["bounds"][2], group[-1]["bounds"][3])
        rules = detect_rules(image, band, sheet, skip=None if selected is None else selected["bounds"])
        rule_total += len(rules)
        for position in rules:
            if not any(abs(position - boundary) <= 3 for boundary in boundaries):
                aligned = False
                lines.append("     第 %d 行在 x=%.0f 处有一条不落在列边界上的竖线" % (index + 1, position))
    check(rule_total >= 3 * len(grouped), "日期格里画出了竖分隔线（共 %d 条）" % rule_total)
    check(aligned, "所有竖分隔线都落在列边界上")

    grid_pixels = np.asarray(image.crop(tuple(grid_box)).convert("RGB"), dtype=np.int16)
    gold = int((np.abs(grid_pixels - np.array(GOLD, dtype=np.int16)).max(axis=2) <= 8).sum())
    check(gold == 0, "日期格区域没有残留的旧金色 #e6c88c（实际 %d 像素）" % gold)

    # ---- 7. 对比度 ----
    text_ratio, accent_ratio = contrast(sheet, TEXT), contrast(sheet, accent)
    check(text_ratio >= 4.5, "日期区底色对正文色的对比度 %.1f:1（WCAG AA 要求 ≥ 4.5）" % text_ratio)
    check(accent_ratio >= 4.5, "日期区底色对主题色的对比度 %.1f:1" % accent_ratio)
    lines.append("说明 上下月日期用的 DIM 色对底色是 %.1f:1，刻意低于 AA：它标的是本月之外的日期，"
                 "应当弱于本月日期，这是设计选择而不是遗漏" % contrast(sheet, DIM))

    # ---- 8. OCR 旁证 ----
    numbers = ocr_digits(Path(arguments.shot).resolve())
    if numbers:
        wanted = {cell.day for cell in cells[:rows * 7]}
        matched = sum(1 for value in numbers if value in wanted)
        lines.append("OCR 数字命中期望日期: %d/%d" % (matched, len(numbers)))
        check(matched >= len(numbers) * 0.8, "OCR 数字与真实公历相符（命中 %d/%d）" % (matched, len(numbers)))
    else:
        lines.append("OCR 未能识别数字（深色渲染下 tesseract 命中率低；日期正确性以上面的视图树比对为准）")

    lines.append("结论: " + ("PASS" if not failures else "FAIL -> " + "; ".join(failures)))
    if arguments.report:
        target = report_path(arguments.report)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("\n".join(lines) + "\n")
    # 控制台只打印 ASCII 摘要：完整报告里有中文，Windows 控制台用 GBK 编码时会把退出码弄成 1。
    print("verify %s: %s (%d checks)" % (arguments.shot, "PASS" if not failures else "FAIL", len(lines)))
    for failure in failures:
        print("  - " + failure.encode("ascii", "replace").decode("ascii"))
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
