"""把 12 个月的首页截图拼成一张对比图，方便一次看完逐月主题色与配色是否协调。

用法：
    python tools/contact_sheet.py --out docs/screenshots/fusion-contact-sheet.png --year 2026

输入是 scripts/verify-fusion.ps1 -AllMonths 生成的 `docs/screenshots/fusion-YYYY-MM.png`。
拼成 4×3 之后，一屏就能比较 12 个月：插画是不是都完整、主题色是不是逐月不同、
日期格的印刷感是不是一致。人眼一次看 12 张独立文件不现实，独立视觉评审也只需要一张图。
"""
import argparse
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw

PROJECT_ROOT = Path(__file__).resolve().parent.parent
MONTH_NAMES = {1: "JAN", 2: "FEB", 3: "MAR", 4: "APR", 5: "MAY", 6: "JUN",
               7: "JUL", 8: "AUG", 9: "SEP", 10: "OCT", 11: "NOV", 12: "DEC"}


def output_path(candidate):
    """输出路径：相对路径按项目根目录解析，绝对路径必须在项目内。

    不能把绝对路径拆成片段再拼到项目根上：Windows 上 `Path("E:")` 不是"绝对路径"
    （它是盘符相对路径），于是 `E:\\...\\docs\\x.png` 会被拼成
    `E:\\...\\E:\\...\\docs\\x.png` ——实测就是这样在仓库里多出一层 `wengfaluosi/` 目录，
    而脚本还打印了"已写入"，看着像成功了。
    """
    resolved = Path(candidate)
    if not resolved.is_absolute():
        resolved = PROJECT_ROOT / resolved
    resolved = resolved.resolve()
    if resolved != PROJECT_ROOT and PROJECT_ROOT not in resolved.parents:
        raise SystemExit("拒绝写到项目目录之外: %s" % resolved)
    return resolved


def main():
    parser = argparse.ArgumentParser(description="把逐月首页截图拼成对比图")
    parser.add_argument("--out", default="docs/screenshots/fusion-contact-sheet.png", help="输出文件")
    parser.add_argument("--year", default="2026")
    parser.add_argument("--columns", type=int, default=4)
    parser.add_argument("--width", type=int, default=270, help="每张缩略图宽度")
    arguments = parser.parse_args()

    tiles = []
    for month in range(1, 13):
        candidate = PROJECT_ROOT / "docs" / "screenshots" / ("fusion-%s-%02d.png" % (arguments.year, month))
        if not candidate.is_file():
            print("缺少 %s，先用 scripts/verify-fusion.ps1 -AllMonths 生成" % candidate.relative_to(PROJECT_ROOT))
            return 1
        with Image.open(candidate) as handle:
            image = handle.convert("RGB")
            scale = arguments.width / float(image.width)
            tiles.append((month, image.resize((arguments.width, max(1, int(round(image.height * scale)))))))

    tile_width, tile_height = tiles[0][1].size
    label_height = 26
    rows = -(-len(tiles) // arguments.columns)
    sheet = Image.new("RGB", (tile_width * arguments.columns, (tile_height + label_height) * rows), (0x10, 0x11, 0x1c))
    draw = ImageDraw.Draw(sheet)
    for index, (month, tile) in enumerate(tiles):
        column, row = index % arguments.columns, index // arguments.columns
        x, y = column * tile_width, row * (tile_height + label_height)
        sheet.paste(tile, (x, y + label_height))
        draw.text((x + 8, y + 6), "%s %s" % (arguments.year, MONTH_NAMES[month]), fill=(0xf4, 0xf0, 0xe7))
        draw.rectangle([x, y, x + tile_width - 1, y + tile_height + label_height - 1], outline=(0x33, 0x33, 0x44))

    target = output_path(arguments.out)
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(target)
    print("已写入 %s（%dx%d）" % (target.relative_to(PROJECT_ROOT), sheet.width, sheet.height))
    return 0


if __name__ == "__main__":
    sys.exit(main())
