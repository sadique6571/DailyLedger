"""Original DailyLedger illustrations for everyday fittings, tools and transport."""
from PIL import Image, ImageDraw

KEYS = set('''adhesive-tape apron ballpoint bathroom-mug buttons cement circuit-breaker clothes-pegs conveyance correction-pen curtain-rod cutting-board diesel door door-handle doorbell drain-cover drill electric-switch electric-wire engine-oil envelope face-mask fertilizer file-folder fly-swatter fuse garden-hose gas-lighter gas-stove gate grater hair-clips hair-ties handkerchief hanger helmet hinges ladle latch marker measuring-tape nails nuts-bolts oven-gloves paint paint-brush paint-roller paper-clips peeler pipe-elbow plant-soil pliers printing-paper puncture rickshaw rolling-pin rope roti-board rubber-bands sand sandpaper sanitizer screws shoe-polish soap-dish socket solar-panel stapler staples sticky-notes strainer tap tawa thermos tiles toilet-brush tongs tube-light tyre tyre-tube washers water-pipe water-tank watering-can window-mesh'''.split())


def render(key):
    if key not in KEYS:
        raise ValueError(key)
    im = Image.new('RGBA', (216, 216))
    d = ImageDraw.Draw(im)
    def box(xy, fill, outline=None, width=2, r=0):
        xy = tuple(round(v * 3) for v in xy)
        if r: d.rounded_rectangle(xy, r * 3, fill, outline, width * 3)
        else: d.rectangle(xy, fill, outline, width * 3)
    def oval(xy, fill, outline=None, width=2): d.ellipse(tuple(round(v * 3) for v in xy), fill, outline, width * 3)
    def line(xy, fill, width=2): d.line(tuple(round(v * 3) for v in xy), fill, width * 3)
    def poly(points, fill): d.polygon([(round(x * 3), round(y * 3)) for x, y in points], fill)
    def arc(xy, start, end, fill, width=2): d.arc(tuple(round(v * 3) for v in xy), start, end, fill, width * 3)
    steel, dark, light = '#A9C2CF', '#456375', '#E6EFF3'
    wood, gold, green = '#B9814D', '#E8B84F', '#499B76'

    if key == 'door':
        box((13, 5, 60, 68), '#76513A', r=2); box((18, 9, 55, 66), wood)
        box((23, 15, 49, 33), '#CB9A65', '#93613E'); box((23, 40, 49, 59), '#CB9A65', '#93613E')
        oval((46, 34, 51, 39), gold); line((8, 68, 65, 68), dark, 2)
    elif key in ('gate', 'window-mesh'):
        box((8, 14, 64, 63), dark, r=2); box((12, 18, 60, 59), '#B9DDE8')
        for x in range(16, 61, 8): line((x, 18, x, 59), steel, 2)
        for y in range(24, 59, 8 if key == 'window-mesh' else 24): line((12, y, 60, y), steel, 2)
        if key == 'gate': line((36, 17, 36, 61), dark, 3); box((29, 37, 43, 41), gold)
    elif key == 'rickshaw':
        poly([(9, 21), (47, 21), (60, 40), (60, 56), (8, 56)], green)
        box((7, 17, 49, 23), gold, r=3); box((13, 27, 30, 44), '#B4E0EC', r=2)
        poly([(36, 27), (45, 27), (53, 40), (36, 40)], '#B4E0EC')
        box((34, 44, 52, 49), gold); line((58, 41, 66, 48), dark, 3)
        for x in (20, 56): oval((x-8, 49, x+8, 65), dark); oval((x-3, 54, x+3, 60), steel)
    elif key == 'conveyance':
        box((6, 16, 48, 46), '#5BA7C0', r=5)
        for x in (12, 24, 36): box((x, 21, x+7, 31), '#D9EDF5', r=1)
        for x in (16, 39): oval((x-5, 41, x+5, 51), dark)
        line((13, 62, 57, 62), green, 3); poly([(57, 57), (65, 62), (57, 67)], green)
        box((48, 29, 66, 53), gold, '#B78E39', r=3); line((51, 36, 63, 36), '#FFF1BD')
    elif key in ('tyre', 'tyre-tube', 'puncture'):
        oval((12, 10, 60, 64), dark); oval((23, 22, 49, 52), (0, 0, 0, 0))
        if key == 'tyre':
            for y in (20, 30, 40, 50): line((14, y, 21, y+4), '#748894', 2); line((52, y+4, 59, y), '#748894', 2)
        elif key == 'tyre-tube': box((48, 8, 53, 19), steel); box((47, 6, 54, 11), gold)
        else: box((41, 41, 63, 57), '#C47C54', r=4); line((46, 49, 58, 49), light, 2); line((52, 44, 52, 54), light, 2)
    elif key == 'helmet':
        oval((11, 12, 61, 59), '#408FAE'); box((36, 29, 64, 43), '#C6E5EB', dark, r=3)
        poly([(9, 46), (28, 46), (34, 60), (57, 60), (58, 49), (64, 49), (64, 66), (20, 66)], '#408FAE')
    elif key in ('diesel', 'engine-oil'):
        poly([(19, 18), (28, 10), (49, 10), (49, 19), (57, 25), (57, 63), (15, 63), (15, 25)], gold if key == 'diesel' else '#C36F53')
        box((30, 14, 44, 21), (0, 0, 0, 0), dark, r=2); box((13, 19, 24, 27), dark, r=2)
        poly([(36, 31), (27, 46), (28, 53), (36, 57), (44, 53), (45, 46)], dark)
    elif key in ('nails', 'screws'):
        for x, y in ((18, 17), (36, 9), (53, 23)):
            box((x-2, y+5, x+2, y+36), steel); poly([(x-2, y+36), (x+2, y+36), (x, y+44)], dark)
            box((x-7, y, x+7, y+5), dark, r=1)
            if key == 'screws':
                for t in range(10, 35, 5): line((x-4, y+t, x+4, y+t-3), dark, 1)
                line((x-4, y+2, x+4, y+2), light, 1)
    elif key in ('nuts-bolts', 'washers'):
        for x, y in ((24, 28), (48, 48)):
            if key == 'nuts-bolts': poly([(x-13, y), (x-7, y-11), (x+7, y-11), (x+13, y), (x+7, y+11), (x-7, y+11)], steel)
            else: oval((x-14, y-14, x+14, y+14), steel, dark)
            oval((x-6, y-6, x+6, y+6), (0, 0, 0, 0), dark, 1)
        if key == 'nuts-bolts': line((11, 60, 54, 16), dark, 6); line((48, 10, 61, 23), steel, 6)
    elif key in ('hinges', 'latch', 'door-handle'):
        if key == 'hinges':
            box((10, 18, 61, 56), gold, r=3); box((32, 14, 39, 60), '#A47A31', r=3)
            for x in (19, 51):
                for y in (27, 47): oval((x-2, y-2, x+2, y+2), dark)
        elif key == 'latch':
            box((9, 24, 53, 49), steel, r=3); box((57, 25, 65, 48), steel, r=2)
            box((16, 32, 64, 40), dark, r=3); line((29, 35, 29, 53), gold, 5)
        else:
            box((19, 12, 38, 62), steel, dark, r=5); box((26, 25, 63, 33), gold, r=4)
            oval((25, 45, 32, 52), dark)
    elif key == 'doorbell':
        box((20, 8, 52, 65), steel, dark, r=8); oval((27, 35, 45, 53), gold, dark)
        for y in (19, 24, 29): line((28, y, 44, y), dark, 1)
    elif key == 'curtain-rod':
        line((9, 24, 63, 24), dark, 5); oval((3, 18, 15, 30), gold); oval((57, 18, 69, 30), gold)
        for x in (20, 34, 48): oval((x-4, 23, x+4, 37), None, steel); line((x, 36, x, 49), steel, 2)
    elif key == 'pliers':
        line((16, 61, 40, 32, 47, 12), dark, 7); line((55, 61, 31, 32, 25, 12), dark, 7)
        line((16, 61, 29, 45), '#D27657', 9); line((55, 61, 42, 45), '#D27657', 9); oval((31, 30, 41, 40), steel)
    elif key == 'drill':
        box((9, 17, 48, 39), '#D09D42', r=5); box((45, 22, 56, 34), dark, r=2); line((56, 28, 68, 28), steel, 4)
        poly([(21, 36), (38, 36), (32, 58), (18, 58)], dark); box((12, 55, 39, 65), '#D09D42', r=3)
    elif key == 'measuring-tape':
        box((10, 17, 51, 56), gold, dark, r=9); oval((20, 26, 40, 46), dark); box((44, 44, 66, 52), light)
        for x in (49, 55, 61): line((x, 44, x, 49), dark, 1)
    elif key in ('paint', 'sand', 'cement', 'fertilizer', 'plant-soil'):
        if key == 'paint':
            box((15, 23, 57, 63), '#72A7C1', dark, r=4); oval((15, 17, 57, 30), light, dark)
            arc((17, 5, 55, 47), 180, 360, dark, 2); box((21, 38, 51, 54), '#F5E8CB', r=3)
            poly([(36, 38), (29, 47), (36, 53), (43, 47)], '#C97C74')
        else:
            poly([(18, 10), (55, 10), (61, 64), (11, 64)], '#CEB28A' if key != 'cement' else '#A5B6BF')
            line((19, 16, 54, 16), dark); box((20, 28, 52, 54), '#F4E7CC', r=3)
            if key in ('fertilizer', 'plant-soil'):
                line((36, 54, 36, 33), green, 3); oval((24, 32, 37, 41), green); oval((36, 27, 48, 37), green)
                if key == 'plant-soil': box((25, 50, 48, 54), '#856342')
            elif key == 'sand': poly([(23, 51), (34, 35), (49, 51)], '#D2B275')
            else: box((25, 35, 47, 47), '#869BA6'); line((36, 35, 36, 47), light, 1)
    elif key in ('paint-brush', 'paint-roller'):
        if key == 'paint-brush':
            box((29, 38, 42, 66), wood, r=4); box((21, 28, 50, 42), steel)
            box((21, 9, 50, 30), '#DABC88'); box((21, 9, 50, 17), '#55A4B4')
            for x in (26, 33, 40, 46): line((x, 17, x, 28), '#AA8854', 1)
        else: box((9, 11, 56, 29), '#68B4B7', r=5); line((56, 19, 63, 19, 63, 39, 37, 39, 37, 49), steel, 3); box((31, 46, 43, 67), wood, r=4)
    elif key in ('sandpaper', 'tiles'):
        if key == 'tiles':
            for x in (9, 37):
                for y in (10, 38): box((x, y, x+25, y+25), '#A8CDD5', dark, 1); line((x+5, y+7, x+18, y+20), light, 2)
        else:
            poly([(15, 11), (61, 18), (54, 64), (8, 57)], '#B58E64')
            for i in range(65): oval((14+i*17%38, 20+i*11%34, 15+i*17%38, 21+i*11%34), '#775B43', width=1)
    elif key in ('rope', 'garden-hose', 'electric-wire', 'rubber-bands', 'hair-ties'):
        color = {'rope':'#B99A67', 'garden-hose':green, 'electric-wire':'#CE785A', 'rubber-bands':gold, 'hair-ties':'#B771A7'}[key]
        for i in range(3): oval((10+i*6, 13+i*5, 60-i*6, 61-i*5), None, color, 3)
        if key == 'garden-hose': line((53, 49, 65, 61), green, 5); box((59, 56, 68, 65), gold, r=2)
        elif key == 'electric-wire': line((52, 51, 65, 63), color, 4); line((65, 63, 69, 67), gold, 2)
        elif key == 'rope': line((19, 52, 8, 65), color, 4)
    elif key == 'adhesive-tape':
        oval((10, 10, 58, 56), '#D4BA7B'); oval((22, 21, 46, 45), (0, 0, 0, 0), '#AE9462')
        poly([(49, 39), (65, 48), (59, 63), (39, 53)], '#D4BA7B')
    elif key in ('electric-switch', 'socket', 'circuit-breaker', 'fuse'):
        if key == 'fuse':
            box((14, 25, 59, 48), '#D5E6EB', dark, r=5); box((10, 25, 22, 48), steel); box((51, 25, 63, 48), steel); line((22, 37, 51, 37), '#B89051', 2)
        else:
            box((14, 10, 59, 64), light, steel, r=6)
            if key == 'electric-switch': box((25, 22, 48, 53), steel, dark, r=3); line((27, 38, 46, 38), light)
            elif key == 'socket':
                oval((22, 22, 51, 53), steel)
                for x in (30, 42): box((x, 32, x+3, 39), dark, r=1)
                box((35, 43, 39, 48), dark, r=1)
            else: box((23, 18, 50, 55), steel); box((26, 30, 47, 42), '#D17055', dark, r=2); line((28, 13, 28, 20), gold, 3); line((45, 13, 45, 20), gold, 3)
    elif key == 'tube-light':
        box((6, 25, 66, 47), steel, r=3); box((11, 29, 61, 42), '#FFF7D9', r=5)
    elif key == 'solar-panel':
        poly([(17, 9), (60, 9), (66, 50), (8, 50)], '#426A99'); line((35, 50, 35, 63), dark, 4); line((20, 64, 52, 64), dark, 4)
        for x in (25, 39, 52): line((x, 11, x-2, 48), '#A7CBE1', 1)
        for y in (22, 36): line((14, y, 62, y), '#A7CBE1', 1)
    elif key in ('tap', 'pipe-elbow', 'water-pipe'):
        if key == 'tap':
            line((18, 64, 18, 31, 52, 31, 52, 43), steel, 10); line((32, 32, 32, 17), dark, 5); line((20, 15, 46, 15), dark, 4)
            poly([(52, 48), (47, 57), (52, 63), (57, 57)], '#67B7D3')
        elif key == 'pipe-elbow': line((16, 61, 16, 23, 58, 23), steel, 15); oval((49, 15, 65, 31), dark); oval((53, 19, 61, 27), '#80A3B3')
        else:
            for x in (13, 30, 47): box((x, 13, x+12, 61), steel); oval((x, 8, x+12, 20), dark); oval((x+3, 11, x+9, 17), '#829EAD')
    elif key == 'water-tank':
        box((12, 16, 61, 63), '#88B5CA', dark, r=10); oval((12, 10, 61, 26), '#B9D5E2', dark)
        for y in (33, 44, 55): line((15, y, 58, y), '#5C92AC', 2)
        box((28, 7, 45, 13), dark, r=2)
    elif key in ('toilet-brush', 'fly-swatter'):
        line((37, 32, 37, 65), '#7CA0AF', 5)
        if key == 'toilet-brush':
            oval((22, 8, 52, 35), '#D2DFE4', dark)
            for y in (15, 22, 29): line((24, y, 50, y), '#94AAB5', 2)
            box((25, 51, 50, 67), '#85A8BD', r=4)
        else:
            box((18, 7, 56, 36), green, r=6)
            for x in range(24, 54, 6): line((x, 11, x, 32), light, 1)
            for y in (15, 22, 29): line((22, y, 52, y), light, 1)
    elif key in ('bathroom-mug', 'soap-dish', 'drain-cover'):
        if key == 'bathroom-mug':
            arc((36, 19, 67, 51), 260, 100, '#679CAF', 5); box((12, 19, 48, 62), '#8DBECF', r=5); oval((12, 14, 48, 29), '#BADBE5', '#679CAF')
        elif key == 'soap-dish': oval((7, 24, 65, 57), steel); box((17, 23, 55, 45), '#DEA5B9', r=8); oval((23, 17, 30, 24), '#F4D9E4'); oval((38, 12, 48, 22), '#F4D9E4')
        else:
            box((10, 10, 62, 62), steel, dark, r=5)
            for x in (20, 31, 42, 53): line((x, 19, x, 53), dark, 4)
    elif key in ('cutting-board', 'roti-board'):
        if key == 'roti-board': oval((9, 16, 63, 63), wood, '#93613E'); oval((15, 21, 57, 57), '#CB9E68')
        else: box((9, 21, 63, 65), wood, r=7); box((28, 7, 44, 28), wood, r=4); oval((33, 11, 39, 17), (0, 0, 0, 0))
        for y in (32, 42, 50): line((20, y, 51, y+2), '#A36C42', 1)
    elif key == 'rolling-pin':
        line((7, 62, 63, 9), '#8C613E', 6); line((20, 50, 50, 21), wood, 16); line((23, 47, 47, 24), '#D1AA76', 3)
    elif key in ('tawa', 'strainer', 'ladle'):
        line((36, 40, 36, 67), wood if key == 'tawa' else dark, 7)
        oval((10, 9, 62, 49), dark if key == 'tawa' else steel)
        if key == 'tawa': oval((18, 16, 54, 43), '#677E89')
        elif key == 'strainer':
            oval((15, 14, 57, 44), '#DAE5E8')
            for x in range(23, 56, 8): line((x, 18, x, 41), '#8FA6B1', 1)
            for y in (23, 30, 37): line((18, y, 54, y), '#8FA6B1', 1)
        else: oval((18, 15, 54, 40), '#DDEAF0')
    elif key in ('peeler', 'tongs'):
        if key == 'peeler': line((35, 37, 35, 64), green, 9); line((35, 39, 17, 15), steel, 5); line((35, 39, 55, 15), steel, 5); line((17, 15, 55, 15), dark, 5)
        else: arc((20, 8, 52, 35), 180, 360, steel, 5); line((20, 22, 13, 62), steel, 5); line((52, 22, 59, 62), steel, 5); box((9, 54, 19, 65), dark, r=3); box((53, 54, 64, 65), dark, r=3)
    elif key == 'grater':
        box((25, 7, 47, 20), None, dark, r=4); poly([(22, 18), (50, 18), (61, 64), (11, 64)], steel)
        for y in (28, 39, 50):
            for x in (25, 36, 47): oval((x-2, y, x+2, y+4), dark)
    elif key in ('thermos', 'sanitizer', 'gas-lighter'):
        if key == 'gas-lighter': box((29, 34, 44, 65), '#D3875B', r=4); box((32, 7, 41, 38), steel); box((30, 5, 43, 12), dark)
        else:
            box((22, 20, 50, 65), '#77AFC2' if key == 'thermos' else '#B5D9D3', dark, r=6)
            box((25, 10, 47, 24), dark, r=3)
            if key == 'sanitizer': line((34, 12, 34, 6, 53, 6), dark, 4); box((26, 36, 46, 53), light, r=3); line((36, 39, 36, 50), green, 3); line((31, 45, 41, 45), green, 3)
            else: arc((42, 24, 65, 53), 270, 90, dark, 4)
    elif key == 'gas-stove':
        box((6, 19, 66, 55), steel, dark, r=5)
        for x in (23, 51): oval((x-11, 24, x+11, 43), dark); oval((x-5, 28, x+5, 39), '#6494B0'); oval((x-3, 46, x+3, 52), dark)
    elif key in ('apron', 'oven-gloves'):
        if key == 'apron': arc((26, 5, 46, 29), 180, 360, '#A56576', 3); poly([(25, 16), (47, 16), (49, 31), (61, 64), (11, 64), (23, 31)], '#D79AAA'); box((26, 40, 46, 54), '#F3CBCA', r=3)
        else:
            for x in (14, 40): box((x, 14, x+19, 59), '#CE9C65', r=8); box((x-8, 28, x+4, 46), '#CE9C65', r=5); box((x, 51, x+19, 63), '#956B4E', r=2)
    elif key == 'hanger':
        arc((27, 8, 41, 25), 170, 430, dark, 3); line((34, 21, 34, 31, 7, 52, 64, 52, 34, 31), wood, 4)
    elif key in ('clothes-pegs', 'hair-clips'):
        for x, y in ((12, 15), (39, 29)):
            box((x, y, x+17, y+37), wood if key == 'clothes-pegs' else '#B97CA8', r=5)
            if key == 'clothes-pegs': line((x+8, y+5, x+8, y+33), '#855F42', 1); box((x-2, y+16, x+19, y+21), steel)
            else: box((x+5, y+6, x+12, y+28), '#F0C7DD', r=3)
    elif key == 'buttons':
        for x, y, c in ((24, 24, '#7DB4C8'), (48, 46, '#D29A78')):
            oval((x-16, y-16, x+16, y+16), c, dark, 1)
            for dx, dy in ((-4,-4),(4,-4),(-4,4),(4,4)): oval((x+dx-1, y+dy-1, x+dx+1, y+dy+1), dark)
    elif key == 'shoe-polish':
        oval((10, 25, 62, 63), dark); oval((10, 19, 62, 53), '#7C8A92', dark); oval((16, 24, 56, 47), gold); poly([(22, 38), (35, 38), (40, 30), (45, 32), (49, 43), (22, 43)], dark)
    elif key == 'handkerchief':
        box((11, 11, 61, 61), '#B1CEDC', r=2); box((17, 17, 55, 55), None, '#E9F3F5', 2); line((11, 11, 61, 61), '#779BAB', 1)
    elif key == 'face-mask':
        arc((3, 22, 25, 54), 80, 280, steel, 3); arc((48, 22, 70, 54), 260, 460, steel, 3); box((14, 18, 59, 57), '#91C8D7', r=6)
        for y in (28, 37, 46): line((20, y, 53, y), '#D0EAF0', 2)
    elif key in ('stapler', 'staples', 'paper-clips'):
        if key == 'stapler': box((9, 49, 64, 58), dark, r=3); poly([(10, 45), (15, 20), (61, 34), (59, 47)], '#6497B1'); line((17, 26, 57, 38), light, 2)
        elif key == 'staples':
            for y in (16, 31, 46): line((15, y+12, 15, y, 58, y, 58, y+12), steel, 3)
        else:
            for x, y in ((12, 9), (35, 26)):
                box((x, y, x+23, y+36), None, steel, 3, r=10); line((x+7, y+9, x+7, y+29, x+16, y+29, x+16, y+5), dark, 2)
    elif key in ('envelope', 'printing-paper', 'file-folder', 'sticky-notes'):
        if key == 'envelope': box((7, 19, 65, 57), '#EAD7AD', r=3); line((9, 21, 36, 42, 63, 21), '#B49568'); line((9, 55, 27, 39), '#B49568'); line((63, 55, 46, 39), '#B49568')
        elif key == 'file-folder': box((8, 13, 32, 25), gold, r=3); box((8, 21, 64, 61), '#D9A94C', r=4); poly([(5, 29), (67, 29), (61, 62), (10, 62)], '#EDC66E')
        elif key == 'sticky-notes': box((12, 14, 62, 64), '#D7B967'); box((8, 8, 58, 58), '#F3D778'); poly([(44, 58), (58, 44), (58, 58)], '#D6B74F')
        else:
            for x, y in ((21, 9), (15, 15), (9, 21)): box((x, y, x+40, y+43), light, steel, 1, r=2)
            for y in (32, 40, 48, 56): line((16, y, 41, y), '#9BB1BE', 1)
    elif key in ('marker', 'ballpoint', 'correction-pen'):
        color = {'marker':'#43788D', 'ballpoint':'#477BB9', 'correction-pen':'#D6E4E9'}[key]
        line((17, 59, 52, 21), color, 10 if key != 'ballpoint' else 6); line((47, 26, 59, 12), dark if key == 'marker' else '#86ABC2', 11 if key != 'ballpoint' else 7)
        poly([(13, 65), (14, 54), (23, 61)], dark); line((51, 15, 57, 21), light, 2)
    elif key == 'watering-can':
        box((17, 28, 50, 61), green, r=6); arc((9, 11, 55, 50), 180, 360, '#397A60', 5)
        poly([(47, 45), (63, 21), (67, 25), (54, 57)], green); line((59, 20, 70, 28), dark, 5)
    else:
        raise ValueError('Missing illustration: ' + key)
    return im.resize((72, 72), Image.Resampling.LANCZOS)
