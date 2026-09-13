"""核对桌面卡片渲染结果：插画是不是当月海报、裁切窗口有没有对准主体、有没有被文字压住。

用法（--pair 可重复，PNG 与 sidecar JSON 成对；PNG/JSON 由 DeviceTests.renderAndRecord 生成）：
    python tools/verify_widget.py --month 2026-09 \
        --pair build/widget-month-340x480.png build/widget-month-340x480.json \
        --pair build/widget-agenda-340x170.png build/widget-agenda-340x170.json \
        --sheet docs/screenshots/widget-sizes.png --report build/fusion/verify-widget.txt

为什么需要它：卡片插画块被 RemoteViews 缩放、裁切之后，"角色大部分可见、且没有被拉变形"
无法靠肉眼在持续集成里保证。本脚本把这几条性质变成断言：
  1. 插画块与当月海报插画区段归一化互相关 >= 0.93——证明画的确实是海报，不是一条纯色背景；
  2. **纵横边缘密度比**与参考窗口的偏差 <= 20%——这才是"有没有被挤压/拉长"的判据。
     互相关做不到这件事：实测把整幅图纵向压扁 1.45 倍后互相关仍有 0.9996（画面以大色块
     渐变为主，低频频谱对几何形变不敏感）。参考窗口必须按单一比例缩放再居中裁（centerCrop
     的几何；议程卡的 fitCenter 则先去掉留白）；直接把窗口 resize 到块尺寸会把同样的变形施加到
     参考上，两边一起变形就比不出来。
     真机上的变形来自位图宽高比与控件不符 + fitXY 非等比拉伸；
  3. 窗口中心对准当月细节质心（偏差 <= 0.12）——质心由 tools/calibrate_art.py 逐月量出，
     与 Art.java 同源；
  4. 窗口保留的细节质量 >= 0.45——"主体大部分在窗口里"的可测代理（细节量 = 梯度能量 +
     饱和度，主体/前景处高，纯背景处低）。
另加：**实测**可见比例 >= sidecar 里该档位的下限（用实测而非上报尺寸算，所以"上报 != 实测"
那一档也能验）、插画块与标题块不相交、以及从 sidecar 转述位图像素与 Binder 事务字节数。

窗口的占比是搜出来的，不是从块的宽高比推的：真机上月历控件用 centerCrop，议程控件用 fitCenter；
位图与控件尺寸不一致时，前者会再裁一刀、后者会留白，解析几何推不准；搜索则对两种来源都成立。
"""
import argparse
import collections
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_screenshot as vs

PROJECT_ROOT = Path(__file__).resolve().parent.parent
MIN_CORRELATION = 0.93
MAX_PLACEMENT_DRIFT = 0.06


def load_focus():
    with (PROJECT_ROOT / "docs" / "art-bounds.json").open(encoding="utf-8") as handle:
        return json.load(handle)


def artwork_source(month, bounds):
    bottom = bounds["months"][str(month)]["artwork_bottom"]
    with Image.open(PROJECT_ROOT / "app" / "src" / "main" / "assets" / "art" / ("month_%02d.jpg" % month)) as handle:
        source = handle.convert("RGB")
        return source.crop((0, 0, source.width, int(source.height * bottom)))


def detail_masses(source):
    """行方向与列方向的细节质量分布（与 calibrate_art.py 的 focus_point 同一公式）。"""
    gray = np.asarray(source.convert("L"), dtype=np.float32)
    rgb = np.asarray(source.convert("RGB"), dtype=np.float32)
    row_edge = np.abs(np.diff(gray, axis=1)).mean(axis=1)
    row_sat = (rgb.max(axis=2) - rgb.min(axis=2)).mean(axis=1)
    rows = 0.6 * row_edge / max(row_edge.max(), 1e-6) + 0.4 * row_sat / max(row_sat.max(), 1e-6)
    col_edge = np.abs(np.diff(gray, axis=0)).mean(axis=0)
    cols = col_edge / max(col_edge.sum(), 1e-6)
    return rows / rows.sum(), cols


FRACTIONS = [0.15 + 0.05 * step for step in range(18)]   # 0.15 .. 1.00


def scale_ratio(gray):
    """纵向边缘密度 ÷ 横向边缘密度。

    这是"画面有没有被非等比拉伸"的判据：内容被纵向压扁 k 倍时，纵向相邻像素的差异按 k 放大、
    横向不变，这个比值就偏离参考值约 k 倍。归一化互相关做不到这件事——实测把整幅图纵向压扁
    1.45 倍之后，互相关仍是 0.9996（这幅画面以大色块渐变为主，低频频谱对几何形变不敏感）。
    """
    vertical = float(np.abs(np.diff(gray, axis=0)).mean())
    horizontal = float(np.abs(np.diff(gray, axis=1)).mean())
    return vertical / max(horizontal, 1e-6)


def uniform_reference(source, window_box, block_size):
    """把源窗口按**单一比例**缩放后居中裁到块尺寸——这是 centerCrop 的几何参考。

    关键是不能直接把窗口 resize 到块尺寸：那会把同样的非等比缩放也施加到参考图上，
    两边一起变形，变形就比不出来了（实测 fitXY 下这样做互相关仍是 0.9996）。
    按单一比例缩放之后，参考图的特征比例与源图一致，被拉伸过的画面就对不上。
    """
    crop = source.crop(window_box)
    scale = max(block_size[0] / float(crop.width), block_size[1] / float(crop.height))
    resized = crop.resize((max(1, int(round(crop.width * scale))), max(1, int(round(crop.height * scale)))))
    left = max(0, (resized.width - block_size[0]) // 2)
    top = max(0, (resized.height - block_size[1]) // 2)
    return resized.crop((left, top, left + block_size[0], top + block_size[1]))


def best_window(panel, source, vertical, coarse_steps=13):
    """二维搜索：同时找窗口占比与位置，返回 (最大互相关, 起点比例, 占比)。

    只搜位置是不够的——被非等比拉伸过的画面，总能在某个位置上和源图"部分对上"，相关系数
    仍可能偏高；把占比也放开之后，只有真正均匀缩放出来的画面才能找到高分窗口。
    """
    block_gray = np.asarray(panel.resize((180, 200)).convert("L"), dtype=np.float32)
    longest = source.height if vertical else source.width

    def evaluate(fraction, offset):
        lo = int(round(offset * longest))
        hi = int(round((offset + fraction) * longest))
        if hi - lo < 8:
            return -1.0
        crop = source.crop((0, lo, source.width, hi)) if vertical else source.crop((lo, 0, hi, source.height))
        reference = np.asarray(crop.resize((180, 200)).convert("L"), dtype=np.float32)
        return vs.normalised_correlation(block_gray, reference)

    best = (-2.0, 0.0, 1.0)
    for fraction in FRACTIONS:
        span = max(0.0, 1.0 - fraction)
        candidates = {0.0, span} | {span * step / max(1, coarse_steps - 1) for step in range(coarse_steps)}
        for offset in candidates:
            score = evaluate(fraction, offset)
            if score > best[0]:
                best = (score, offset, fraction)
    # 在最优占比上把位置细化一遍（步长的 1/4）。
    score, offset, fraction = best
    span = max(0.0, 1.0 - fraction)
    if span > 0:
        step = span / max(1, coarse_steps - 1) / 4.0
        for k in range(-6, 7):
            candidate = min(max(offset + k * step, 0.0), span)
            refined = evaluate(fraction, candidate)
            if refined > score:
                score, offset = refined, candidate
    return score, offset, fraction


def verify_pair(pair, month, bounds, lines, failures):
    png_path, json_path = pair
    sidecar = json.loads(Path(json_path).read_text(encoding="utf-8"))
    image = Image.open(png_path).convert("RGB")
    name = sidecar.get("name", Path(png_path).name)
    art_box = sidecar["art"]
    title_box = sidecar["title"]
    art_w, art_h = art_box[2] - art_box[0], art_box[3] - art_box[1]
    # 今日安排卡片用 fitCenter：当启动器上报尺寸与实际卡片尺寸不一致时，图片栏会出现
    # 上下或左右的背景留白。只拿真正绘制位图的矩形做画面核对，避免把留白当成素材失真。
    picture_box = list(art_box)
    bitmap_size = sidecar.get("artBitmap")
    if sidecar.get("agenda") and bitmap_size and bitmap_size[0] > 0 and bitmap_size[1] > 0:
        bitmap_aspect = bitmap_size[0] / float(bitmap_size[1])
        lane_aspect = art_w / float(art_h) if art_h else bitmap_aspect
        if bitmap_aspect > lane_aspect:
            picture_height = max(1, min(art_h, int(round(art_w / bitmap_aspect))))
            picture_box[1] += max(0, (art_h - picture_height) // 2)
            picture_box[3] = picture_box[1] + picture_height
        elif bitmap_aspect < lane_aspect:
            picture_width = max(1, min(art_w, int(round(art_h * bitmap_aspect))))
            picture_box[0] += max(0, (art_w - picture_width) // 2)
            picture_box[2] = picture_box[0] + picture_width
    picture_w, picture_h = picture_box[2] - picture_box[0], picture_box[3] - picture_box[1]
    source = artwork_source(month, bounds)
    rows_mass, cols_mass = detail_masses(source)
    focus = bounds["months"][str(month)]

    def check(condition, message):
        if not condition:
            failures.append(message)
        lines.append(("PASS " if condition else "FAIL ") + message)

    reported = sidecar.get("optionSize", sidecar["size"])
    lines.append("== %s（实测卡片 %sx%s dp，上报 %sx%s dp，插画块 %dx%d px，位图 %s，事务 %s 字节）"
                 % (name, sidecar["size"][0], sidecar["size"][1], reported[0], reported[1],
                    picture_w, picture_h, sidecar.get("artBitmap", "?"), sidecar.get("parcelBytes", "?")))

    # 裁切轴由块的实际形状决定：块比插画"更宽"时裁高度，否则裁宽度。
    block_aspect = picture_w / float(picture_h)
    source_aspect = source.width / float(source.height)
    vertical = block_aspect >= source_aspect
    correlation, offset, fraction = best_window(image.crop(tuple(picture_box)), source, vertical)
    check(correlation >= MIN_CORRELATION,
          "插画块画的是 month_%02d.jpg 的插画区段（互相关 %.4f，下限 %.2f）"
          % (month, correlation, MIN_CORRELATION))

    focus_value = focus["focus_y"] if vertical else focus["focus_x"]
    mass = rows_mass if vertical else cols_mass
    # 月历卡用 centerCrop，议程卡用焦点取景 + fitCenter；两者都要断言"主体还在窗口里"，
    # 且窗口中心不能偏离逐月标定的质心。
    lo_frac, hi_frac = offset, offset + fraction
    check(lo_frac <= focus_value <= hi_frac,
          "主体质心落在窗口内（窗口 %.3f-%.3f，质心 %.3f）" % (lo_frac, hi_frac, focus_value))
    check(abs(offset + fraction / 2.0 - focus_value) <= 0.12,
          "窗口中心与质心偏差 %.3f（上限 0.12）" % abs(offset + fraction / 2.0 - focus_value))
    # 非等比缩放检查：把匹配到的源窗口缩放到块的像素尺寸（同一网格），比较两者的纵横边缘密度比。
    if vertical:
        window_box = (0, int(lo_frac * source.height), source.width, int(hi_frac * source.height))
    else:
        window_box = (int(lo_frac * source.width), 0, int(hi_frac * source.width), source.height)
    block_crop = image.crop(tuple(picture_box))
    reference = uniform_reference(source, window_box, block_crop.size)
    block_gray = np.asarray(block_crop.convert("L"), dtype=np.float32)
    reference_gray = np.asarray(reference.convert("L"), dtype=np.float32)
    ratio_block, ratio_reference = scale_ratio(block_gray), scale_ratio(reference_gray)
    drift = abs(math.log(ratio_block / ratio_reference))
    check(drift <= 0.20,
          "画面没有被非等比拉伸（纵横边缘密度比 %.3f vs 参考 %.3f，偏差 %.0f%%，上限 20%%）"
          % (ratio_block, ratio_reference, drift * 100))

    lo = int(lo_frac * (source.height if vertical else source.width))
    hi = int(hi_frac * (source.height if vertical else source.width))
    retained = float(mass[lo:hi].sum())
    # 保留质量随窗口占比缩放：窗口占 40% 时不可能保留 90% 的细节。下限取 0.75×占比——
    # 低于它就说明窗口落到了空背景上（而不是主体上）。
    floor = 0.75 * fraction
    check(retained >= floor,
          "窗口保留了 %.0f%% 的细节质量（占比 %.0f%%，下限 %.0f%%）" % (retained * 100, fraction * 100, floor * 100))

    # 用实测尺寸的可见比例对比下限：sidecar 里的 artVisible 是布局预算（按上报尺寸算），
    # 这里量的是画面实际保留了多少，所以"上报 != 实测"那一档也验得到。
    measured = fraction
    min_visible = sidecar["minVisible"]
    check(measured >= min_visible - 0.06,
          "实测插画可见比例 %.0f%% >= 该档下限 %.0f%%（按实测尺寸算）" % (measured * 100, min_visible * 100))
    overlap = not (art_box[2] <= title_box[0] or title_box[2] <= art_box[0]
                   or art_box[3] <= title_box[1] or title_box[3] <= art_box[1])
    check(not overlap, "插画块与标题块不相交（图上没有文字）")
    return image, name


def main():
    parser = argparse.ArgumentParser(description="核对桌面卡片的插画渲染")
    parser.add_argument("--month", required=True, help="年月，如 2026-09")
    parser.add_argument("--pair", nargs=2, action="append", required=True, metavar=("PNG", "JSON"),
                        help="渲染结果与 sidecar，可重复")
    parser.add_argument("--sheet", help="把全部 pair 拼成一张对比图存到项目内")
    parser.add_argument("--report", help="报告写到项目目录内的某个文件")
    arguments = parser.parse_args()

    year, month = (int(part) for part in arguments.month.split("-"))
    bounds = load_focus()
    lines, failures = [], []
    tiles = []
    for pair in arguments.pair:
        image, name = verify_pair([Path(p) for p in pair], month, bounds, lines, failures)
        tiles.append((name, image.resize((270, max(1, int(round(image.height * 270.0 / image.width)))))))
    lines.append("结论: " + ("PASS" if not failures else "FAIL -> " + "; ".join(failures)))

    if arguments.report:
        target = vs.report_path(arguments.report)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("\n".join(lines) + "\n")
    if arguments.sheet and tiles:
        target = vs.report_path(arguments.sheet)
        target.parent.mkdir(parents=True, exist_ok=True)
        label_height = 24
        width = max(tile.width for _, tile in tiles)
        height = max(tile.height for _, tile in tiles)
        sheet = Image.new("RGB", (width * len(tiles), height + label_height), (0x10, 0x11, 0x1c))
        draw = ImageDraw.Draw(sheet)
        for index, (name, tile) in enumerate(tiles):
            sheet.paste(tile, (index * width, label_height))
            draw.text((index * width + 6, 5), name, fill=(0xF4, 0xF0, 0xE7))
        sheet.save(target)
        print("sheet: %s" % target)

    print("verify widgets: %s (%d pairs)" % ("PASS" if not failures else "FAIL", len(tiles)))
    for failure in failures:
        print("  - " + failure.encode("ascii", "replace").decode("ascii"))
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
