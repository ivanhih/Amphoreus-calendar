"""把 uiautomator 的视图树 XML 解析成"哪个区域在哪"，供截图核对定位用。

为什么需要它：`verify_screenshot.py` 原来把 1080x2400 @420 下的像素框**写死在代码里**
（品牌 `(53,74,881,154)`、格子 `grid_x0,grid_y0=54,1424` 等）。首页版式一改，那些框全部
失效、脚本必然报 FAIL；更要紧的是它没法在别的分辨率上跑，所以"换个屏幕尺寸再验一遍"
这件事做不到。改成从视图树取区域之后，同一份核对脚本能跑 1080x1920 / 1440x3120 / 平板。

视图树里的定位依据是 content description 与文本，两者都是应用自己设置的、可访问性用途的
字段，不是为了测试临时加的：

- 插画面板：`content-desc` 里含"月历插画"
- 日期格：`content-desc` 形如"9月10日 星期四，今天，有日程"（由 MainActivity 设置）
- 星期行：一行里并排的七个单字（一/二/三/四/五/六/日）
- 年月标题：含四位年份与"·"的文本
- 底部 tab：文本里含"月历"/"日程"
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ElementTree


def _bounds(node):
    """把 bounds="[x0,y0][x1,y1]" 解析成 (x0,y0,x1,y1)，缺失或畸形返回 None。"""
    raw=node.get("bounds","")
    match=re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]",raw)
    return None if match is None else tuple(int(value) for value in match.groups())


def _union(boxes):
    boxes=[box for box in boxes if box]
    if not boxes:
        return None
    return (min(b[0] for b in boxes),min(b[1] for b in boxes),max(b[2] for b in boxes),max(b[3] for b in boxes))


def _centre(box):
    return ((box[0]+box[2])/2.0,(box[1]+box[3])/2.0)


def load(path):
    """解析视图树，返回一个字典：屏幕尺寸、各区域 bounds、日期格列表、所有文本节点。"""
    root=ElementTree.parse(path).getroot()
    nodes=[node for node in root.iter("node")]
    described=[]
    labelled=[]
    for node in nodes:
        box=_bounds(node)
        if box is None:
            continue
        described.append((node.get("content-desc") or "",box,node))
        if (node.get("text") or "").strip():
            labelled.append(((node.get("text") or "").strip(),box,node))
    if not described:
        raise SystemExit("视图树里没有可用的节点，确认 uiautomator dump 成功、且应用在前台")
    screen=_union([box for _,box,_ in described])

    art=None
    for description,box,node in described:
        if "月历插画" in description:
            art=box
            break

    # 日期格：content-desc 是"9月10日 星期四…"，并且是 clickable 的。
    # 两个条件都要：插画面板的 content-desc 是"9月1日 星期二至9月30日 星期三月历插画…"，
    # 同样含"9月1日 星期"，只按正则匹配会把它也当成一个日期格（实测就多出第 36 格、
    # 还把 grid 的区域并到了插画顶上）。
    cells=[]
    for description,box,node in described:
        if "插画" in description or node.get("clickable")!="true":
            continue
        match=re.match(r"(\d+)月(\d+)日 星期",description)
        if match:
            cells.append({
                "month":int(match.group(1)),
                "day":int(match.group(2)),
                "today":"今天" in description,
                "event":"有日程" in description,
                "bounds":list(box),
            })
    # 视图树是深度优先的，但同一行的顺序未必稳定；按 y 再按 x 排序，得到阅读顺序。
    cells.sort(key=lambda cell:(round(_centre(cell["bounds"])[1]/8.0),_centre(cell["bounds"])[0]))

    weekday=None
    weekdays=[("一","二","三","四","五","六","日"),("日","一","二","三","四","五","六")]
    for labels in weekdays:
        found=[(text,box) for text,box,_ in labelled if text in labels]
        if len(found)>=7:
            # 取同一行的七个：按 y 分组后选最上面那一组。
            found.sort(key=lambda item:_centre(item[1])[1])
            first_y=_centre(found[0][1])[1]
            row=[item for item in found if abs(_centre(item[1])[1]-first_y)<=max(8,first_y*0.02)]
            if len(row)>=7:
                weekday=_union([box for _,box in row])
                break

    title=None
    for text,box,_ in labelled:
        if re.search(r"\d{4}",text) and "·" in text:
            title=box
            break

    tabs={}
    for text,box,_ in labelled:
        for name in ("月历","日程"):
            # 取最下面那个匹配：底部导航是钉在屏幕底部的，而正文里也可能出现"日程"这两个字
            # （空日程卡的文案就含"日程"），取第一个会把正文当成导航。
            if name in text and (name not in tabs or box[1]>tabs[name][1]):
                tabs[name]=box

    return {
        "screen":{"width":screen[2]-screen[0],"height":screen[3]-screen[1],"origin":[screen[0],screen[1]]},
        "art":None if art is None else list(art),
        "grid":None if not cells else list(_union([tuple(cell["bounds"]) for cell in cells])),
        "weekday":None if weekday is None else list(weekday),
        "title":None if title is None else list(title),
        "tabs":{name:list(box) for name,box in tabs.items()},
        "cells":cells,
        "texts":[{"text":text,"bounds":list(box)} for text,box,_ in labelled],
    }


def rows(cells, tolerance=8):
    """把日期格按行分组，返回 [[cell,...], ...]，行内按 x 排序。"""
    grouped=[]
    for cell in sorted(cells,key=lambda item:_centre(item["bounds"])[1]):
        if grouped and abs(_centre(grouped[-1][0]["bounds"])[1]-_centre(cell["bounds"])[1])<=tolerance:
            grouped[-1].append(cell)
        else:
            grouped.append([cell])
    for group in grouped:
        group.sort(key=lambda item:_centre(item["bounds"])[0])
    return grouped


def main():
    parser=argparse.ArgumentParser(description="解析 uiautomator 视图树里的区域")
    parser.add_argument("tree",help="uiautomator dump 出来的 XML")
    parser.add_argument("--json",action="store_true",help="输出 JSON（默认输出人读的摘要）")
    arguments=parser.parse_args()
    tree=load(arguments.tree)
    if arguments.json:
        print(json.dumps(tree,ensure_ascii=False,indent=2,sort_keys=True))
        return 0
    print("屏幕 %dx%d" % (tree["screen"]["width"],tree["screen"]["height"]))
    for name in ("art","grid","weekday","title"):
        box=tree[name]
        if box is None:
            print("%-8s 未找到" % name)
        else:
            print("%-8s [%d,%d]-[%d,%d]  %dx%d" % (name,box[0],box[1],box[2],box[3],box[2]-box[0],box[3]-box[1]))
    if tree["tabs"]:
        print("tab     "+"  ".join("%s[%d,%d]" % (name,box[0],box[1]) for name,box in sorted(tree["tabs"].items())))
    grid_rows=rows(tree["cells"])
    print("日期格 %d 个，%d 行：%s" % (len(tree["cells"]),len(grid_rows),
        " ".join("%d 列" % len(group) for group in grid_rows)))
    for group in grid_rows[:1]+grid_rows[-1:]:
        days=["%d/%d%s" % (cell["month"],cell["day"],"*" if cell["today"] else "") for cell in group]
        print("   行 y=%d: %s" % (group[0]["bounds"][1]," ".join(days)))
    return 0


if __name__=="__main__":
    sys.exit(main())
