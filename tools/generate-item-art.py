#!/usr/bin/env python3
"""Render DailyLedger's original item pictograms (Pillow, no network or font dependency).
Twemoji PNGs are separate CC-BY-4.0 assets; see assets/ITEM-ART-LICENSE.md.
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw
from item_art_everyday import KEYS as EVERYDAY_KEYS, render as render_everyday

DEST = Path(__file__).resolve().parents[1] / 'app/src/main/assets/item-art'
S = 3

def render(key):
    im = Image.new('RGBA', (216,216)); d = ImageDraw.Draw(im)
    def box(xy,fill,outline=None,width=2,r=0):
        coords=tuple(int(v*S) for v in xy)
        if r:d.rounded_rectangle(coords,r*S,fill,outline,width*S)
        else:d.rectangle(coords,fill,outline,width*S)
    def oval(xy,fill,outline=None,width=1):d.ellipse(tuple(int(v*S) for v in xy),fill,outline,width*S)
    def line(xy,fill,width=2):d.line(tuple(int(v*S) for v in xy),fill,width*S)
    def poly(xy,fill):d.polygon([(int(x*S),int(y*S)) for x,y in xy],fill)
    def arc(xy,a,b,fill,width=2):d.arc(tuple(int(v*S) for v in xy),a,b,fill,width*S)
    def leaf(x,y,size=8,c='#49A34D'):
        oval((x,y,x+size,y+size/2),c);line((x+1,y+size/3,x+size-1,y+size/4),'#28783A',1)
    def bowl(color, seeds=False):
        oval((8,26,64,59),'#92B6CD');oval((8,20,64,43),'#E1EFF6');oval((12,23,60,39),color)
        poly([(9,34),(63,34),(54,57),(18,57)],'#A3C5DC');arc((8,20,64,43),0,180,'#6F99B7')
        if seeds:
            for i in range(24):
                x=16+(i*17%39);y=26+(i*7%9);oval((x,y,x+3,y+2),seeds)
    def jar(color,content='#F3E7CB'):
        box((18,17,54,63),'#E5EDF0','#90A4AE',r=7);box((21,25,51,57),content,r=3)
        box((18,9,54,20),color,r=3)
        # A picture of the contents, rather than tiny unreadable text.
    def bottle(color,cap='#29576C',pump=False):
        box((21,24,52,64),color,r=7);box((29,14,44,27),color,r=3)
        box((28,9,46,17),cap,r=2)
        if pump:box((29,6,55,10),cap,r=2);box((48,8,55,15),cap,r=1)
        box((25,35,48,53),'#FFFFFFB8',r=4)
    def packet(color):
        poly([(16,12),(56,12),(59,64),(13,64)],color);line((17,16,55,16),'#FFFFFF99');line((14,60,58,60),'#FFFFFF99')
        box((21,26,51,51),'#FFF8E7',r=5)
    def fabric(color,quilt=False):
        box((9,15,63,59),color,r=6)
        if quilt:
            for x in range(12,64,10):line((x,17,x,58),'#FFFFFF88',1)
            for y in range(19,59,9):line((10,y,62,y),'#FFFFFF88',1)
        else:
            for y in [24,37,49]:line((12,y,60,y),'#FFFFFF88',2)
    def seeds(kind):
        colors={'lentils-red':'#ED8655','lentils-yellow':'#E9B63A','lentils-white':'#EEE7D2','fennel':'#88A65A','cumin':'#927044','carom':'#9B8759','nigella':'#333437','mustard-seeds':'#B88B2D','pepper':'#43403C','chickpeas':'#D6AC68','roasted-chickpeas':'#BA7D34','bird-feed':'#CCA55A','fenugreek':'#CAA246'}
        c=colors[kind];bowl('#ECD6AC')
        for i in range(33):
            x=15+(i*13%43);y=25+(i*7%11)
            if kind in ['fennel','cumin','carom']:oval((x,y,x+5,y+2),c)
            else:oval((x,y,x+3,y+3),c)
    if key in EVERYDAY_KEYS:
        return render_everyday(key)
    if key=='curtains':
        box((12,10,60,61),'#D5EFF8','#6A8493');line((36,11,36,60),'#FFFFFF',2);line((13,35,59,35),'#FFFFFF',2)
        line((6,8,66,8),'#725542',4)
        poly([(9,11),(31,11),(22,35),(13,60),(8,60)],'#B04368');poly([(41,11),(63,11),(64,60),(59,60),(50,35)],'#B04368')
        for x in [12,19,26]:line((x,13,x-2,27),'#E381A1',2)
        for x in [46,53,60]:line((x,13,x+2,27),'#E381A1',2)
        line((12,36,22,34),'#EFC45F',3);line((50,34,60,36),'#EFC45F',3)
    elif key in ['bedsheet','blanket','quilt','towel','carpet','doormat','dupatta']:
        fabric({'bedsheet':'#85B8DB','blanket':'#B36F9D','quilt':'#439A96','towel':'#E5B568','carpet':'#9C546C','doormat':'#B68E60','dupatta':'#CD769E'}[key],key in ['quilt','carpet','doormat'])
        if key in ['carpet','doormat','dupatta']:
            for x in range(13,63,5):line((x,11,x,16),'#B08766',1);line((x,59,x,64),'#B08766',1)
    elif key=='pillow':
        box((9,19,63,55),'#B0CCEA','#6F8DB3',r=10);arc((15,22,58,51),200,330,'#EAF5FF',3)
    elif key=='table':
        box((8,21,64,31),'#B47D49',r=3)
        for x in [14,52]:box((x,30,x+6,63),'#805539',r=2)
    elif key in ['ginger','dry-ginger','arvi']:
        c='#D8AD71' if key!='arvi' else '#997650'
        for b in [(15,26,39,48),(30,17,47,45),(35,34,61,49),(20,40,36,57)]:oval(b,c,'#AA8050')
        for x,y in [(23,33),(37,25),(43,41)]:line((x,y,x+4,y+6),'#966D48',1)
    elif key in ['coriander','mint','bay-leaves','fenugreek'] and key!='fenugreek':
        for i in range(6):
            x=14+i*7;line((35,62,x+4,22+i%3*8),'#52964C',2);leaf(x,18+i%3*8,13)
    elif key in ['peas','green-beans','okra','turai','karela','lauki']:
        for i in range(3):
            x=12+i*17;box((x,16+i%2*6,x+12,62-i%2*5),'#63A145' if key!='lauki' else '#B4CC7B',r=6)
            line((x+6,12+i%2*6,x+6,17+i%2*6),'#497A32',3)
            if key in ['peas','green-beans']:
                for y in range(23,55,8):oval((x+3,y,x+9,y+6),'#B9D774')
            else:
                for y in range(24,56,7):line((x+3,y,x+8,y+1),'#3B833E',1)
    elif key in ['cauliflower','tinda','guava','turnip','radish','beetroot','pomegranate','papaya','apricot','plum','spring-onions']:
        if key=='spring-onions':
            for x in [20,34,48]:
                line((x,45,x-8,10),'#62A84D',4);line((x,45,x+3,9),'#368C43',3);oval((x-5,40,x+6,57),'#F3ECE0');line((x,57,x-2,63),'#B99B78',1)
        elif key=='cauliflower':
            poly([(14,42),(58,42),(49,63),(24,63)],'#6DA64A')
            for x,y in [(15,27),(28,18),(40,23),(25,32),(43,35)]:oval((x,y,x+19,y+18),'#F1E9CF','#D4CCB1')
        elif key=='papaya':
            oval((14,12,58,64),'#F5AA3D');oval((26,20,46,56),'#FBC875')
            for i in range(18):x=29+i*7%13;y=27+i*5%22;oval((x,y,x+3,y+3),'#4E352C')
        else:
            c={'tinda':'#9DBD62','guava':'#AFC56C','turnip':'#EEE7DA','radish':'#F5ECE3','beetroot':'#9C355A','pomegranate':'#C54348','apricot':'#F5B653','plum':'#77527F'}[key]
            oval((14,21,58,61),c);line((36,13,36,24),'#765B37',3);leaf(37,12,15)
            if key in ['radish','turnip','beetroot']:line((36,59,33,68),'#C4AC91',2)
            if key=='pomegranate':
                poly([(27,20),(27,11),(33,16),(38,10),(44,20)],'#A1313A');oval((36,35,61,62),'#F4C4AA')
                for i in range(10):x=40+i*7%15;y=40+i*5%15;oval((x,y,x+3,y+4),'#B73248')
    elif key in ['almonds','walnuts','pistachios','cashews','raisins','figs','pine-nuts','dried-apricots','dried-plums','dates','beans','tamarind','cardamom']:
        for i in range(5):
            x=10+i*11%45;y=14+i*17%33
            c={'almonds':'#B97F47','walnuts':'#A0784C','pistachios':'#C4BF80','cashews':'#ECD19A','raisins':'#6E4A48','figs':'#A47C61','pine-nuts':'#E5D6B0','dried-apricots':'#DB9443','dried-plums':'#5B3F55','dates':'#8E5536','beans':'#974B3F','tamarind':'#A27646','cardamom':'#9AAF69'}[key]
            if key=='walnuts':
                for dx,dy in [(1,2),(8,0),(0,10),(9,9),(4,17)]:oval((x+dx,y+dy,x+dx+10,y+dy+11),c,'#896841')
                line((x+9,y+3,x+8,y+24),'#6F5037',2)
                continue
            if key=='almonds':
                poly([(x+9,y),(x+17,y+11),(x+13,y+21),(x+9,y+25),(x+3,y+20),(x,y+11)],c)
            else:oval((x,y,x+18,y+24),c,'#896841')
            if key=='cashews':oval((x+8,y+3,x+22,y+18),(0,0,0,0))
            elif key=='pistachios':oval((x+6,y+4,x+13,y+20),'#73934B')
            else:line((x+9,y+4,x+9,y+20),'#744F33',1)
    elif key in ['cinnamon','cloves','star-anise','saffron']:
        if key=='cinnamon':
            for y in [21,34,47]:box((12,y,59,y+9),'#AA6B3C',r=4);oval((51,y,61,y+9),'#774429');oval((54,y+2,59,y+7),'#C89160')
        elif key=='cloves':
            for x,y in [(16,19),(35,13),(49,33),(25,38)]:line((x+3,y+6,x+8,y+25),'#79533A',4);oval((x-2,y,x+8,y+11),'#684634')
        elif key=='star-anise':
            for i in range(8):
                a=i*math.pi/4;poly([(36,36),(36+25*math.cos(a),36+25*math.sin(a)),(36+9*math.cos(a+.5),36+9*math.sin(a+.5))],'#A56B3C')
            oval((31,31,41,41),'#70452C')
        else:
            for i in range(10):line((15+i*4,53,21+i*3,20),'#B64235',2)
    elif key in ['lentils-red','lentils-white','lentils-yellow','fennel','cumin','carom','nigella','mustard-seeds','pepper','chickpeas','roasted-chickpeas','bird-feed','fenugreek']:seeds(key)
    elif key in ['curry','haleem','minced-meat','liver','kheer','ras-malai','fruit-salad','paste','yogurt','cream']:
        c={'curry':'#C67C32','haleem':'#C09B5A','minced-meat':'#CC8171','liver':'#8D4F42','kheer':'#EEE2B9','ras-malai':'#F1E6C3','fruit-salad':'#F2C17A','paste':'#D4BF7C','yogurt':'#FFF9E9','cream':'#FFF7DB'}[key]
        bowl(c)
        for i in range(8):
            x=19+i*13%35;y=27+i*7%8
            if key in ['curry','liver','minced-meat']:oval((x,y,x+7,y+5),'#7F4C2B')
            elif key=='fruit-salad':box((x,y,x+5,y+4),['#B85742','#8FB94C','#E3A44F'][i%3])
            elif key=='ras-malai':oval((x,y,x+9,y+5),'#E7CE85')
            else:line((x,y,x+4,y+2),'#A98F57',1)
    elif key in ['roti','papar','dosa','halwa-puri','samosa','nuggets','brittle','rusk','jalebi','gulab-jamun']:
        oval((5,40,67,65),'#D4E5ED');oval((9,42,63,61),'#F0F5F4')
        if key in ['roti','papar','dosa','halwa-puri']:
            oval((12,20,61,52),'#E5B86F','#BA884A')
            if key=='dosa':poly([(12,37),(58,24),(54,48)],'#D3A35B')
            for i in range(16):x=18+i*17%36;y=26+i*7%18;oval((x,y,x+2,y+2),'#B98A48')
            if key=='halwa-puri':oval((39,38,63,54),'#DB8A33')
        elif key=='samosa':
            for x,y in [(12,30),(32,19)]:poly([(x,y+26),(x+14,y),(x+28,y+26)],'#DCAA53');line((x,y+26,x+14,y),'#BA863C',2)
        elif key=='jalebi':
            for x,y in [(13,22),(30,16),(40,32),(20,35)]:oval((x,y,x+20,y+17),'#EC9626');oval((x+5,y+4,x+15,y+13),(0,0,0,0))
        elif key=='gulab-jamun':
            for x,y in [(13,28),(29,20),(42,33)]:oval((x,y,x+19,y+19),'#9C5331');oval((x+3,y+3,x+8,y+6),'#C38556')
        else:
            for x,y in [(12,23),(31,17),(28,39)]:box((x,y,x+25,y+17),'#DCA95C','#B47E37',r=4)
    elif key in ['milk-carton','milk-powder','formula','condensed-milk']:
        if key=='milk-carton':
            poly([(20,19),(31,8),(52,18),(52,63),(20,63)],'#E7F4FC');poly([(20,19),(31,8),(40,19)],'#78ACD5');box((21,28,51,48),'#76B4DC');oval((28,32,44,46),'#FAFEFF')
        else:jar('#77A5CE','#FFF2D3');oval((27,33,46,51),'#FFFFFF')
    elif key in ['oil','olive-oil','mustard-oil','vinegar','soy-sauce','syrup','sauce-red','sauce-green','dressing','water-bottle','energy-drink','lassi','falooda']:
        if key in ['lassi','falooda']:
            poly([(18,21),(54,21),(49,64),(23,64)],'#FCF0D3' if key=='lassi' else '#F3B1BC');oval((18,16,54,26),'#FFF6E6');line((42,21,48,7),'#B87C99',3)
        else:
            colors={'oil':'#DAB84B','olive-oil':'#87974B','mustard-oil':'#C79F32','vinegar':'#D7B76A','soy-sauce':'#644333','syrup':'#9A6140','sauce-red':'#CD5645','sauce-green':'#659951','dressing':'#E1C982','water-bottle':'#82BFDA','energy-drink':'#83AC4C'}
            bottle(colors[key]);
            if key=='olive-oil':leaf(28,40,14,'#537B39')
    elif key in ['flour','flour-yellow','sugar','salt','brown-sugar','jaggery','baking-powder','baking-soda','yeast','custard','cocoa','spices','turmeric','chilli-flakes','tea-leaves','vermicelli','ghee','jam','peanut-butter','mayonnaise','pickle','chips']:
        c={'flour':'#D6BE9B','flour-yellow':'#DFBD65','sugar':'#78B6C3','salt':'#B3B9C7','brown-sugar':'#AE8B61','jaggery':'#AC774C','baking-powder':'#B67E9E','baking-soda':'#7FADC3','yeast':'#C49E65','custard':'#E6B348','cocoa':'#8E644C','spices':'#C58B48','turmeric':'#E0AB2E','chilli-flakes':'#C76545','tea-leaves':'#6E9975','vermicelli':'#D4B579','ghee':'#DFBB54','jam':'#B55E73','peanut-butter':'#B78750','mayonnaise':'#E2D2AB','pickle':'#A89444','chips':'#D8AD59'}[key]
        if key in ['ghee','jam','peanut-butter','mayonnaise','pickle']:jar(c,c)
        else:packet(c)
        if key=='tea-leaves':leaf(27,35,18)
        elif key=='vermicelli':
            for i in range(6):line((25+i*4,31,22+i*4,47),'#B28A45',1)
        elif key=='chips':
            for x,y in [(23,30),(34,32),(28,40)]:oval((x,y,x+12,y+8),'#ECC564','#C7953C')
        else:oval((28,33,44,45),'#FFF5D6' if key not in ['cocoa','spices','turmeric','chilli-flakes'] else c)
    elif key in ['shampoo','conditioner','hair-oil','handwash','baby-shampoo','baby-lotion','lotion','facewash','sunscreen','floor-cleaner','toilet-cleaner','dish-liquid','bleach','laundry-liquid','deodorant','perfume']:
        c={'shampoo':'#91A7D9','conditioner':'#B7A0CF','hair-oil':'#B3A458','handwash':'#7DBDB4','baby-shampoo':'#E8CB7E','baby-lotion':'#D4AEC9','lotion':'#DDA4B3','facewash':'#7DBAC4','sunscreen':'#EDBD69','floor-cleaner':'#7CA98D','toilet-cleaner':'#697FC0','dish-liquid':'#96BA60','bleach':'#ABC1CF','laundry-liquid':'#C0A1D3','deodorant':'#788C9C','perfume':'#C8A0BB'}[key]
        bottle(c,pump=key in ['handwash','baby-lotion','lotion','perfume'])
        if key=='toilet-cleaner':box((40,7,51,15),'#C77379',r=2)
        if key in ['baby-shampoo','baby-lotion']:oval((30,39,42,50),'#F4DFAE')
    elif key in ['washing-powder','dish-soap','sanitary-pads','wipes','garbage-bags','tissues','cat-litter']:
        packet({'washing-powder':'#86ACD2','dish-soap':'#8BBA68','sanitary-pads':'#B8A8D5','wipes':'#A1C6B4','garbage-bags':'#6C8980','tissues':'#B5C2DF','cat-litter':'#D5BC8E'}[key])
        if key=='tissues':poly([(27,25),(22,7),(36,14),(49,8),(45,26)],'#F4FAFD')
        if key=='sanitary-pads':box((30,29,42,51),'#FFFFFF',r=6)
        if key=='garbage-bags':poly([(23,54),(28,32),(40,32),(46,54)],'#42665B')
    elif key=='mosquito':
        for a in [0,60,120]:line((23,30,52,51),'#585C6A',2)
        oval((29,27,43,49),'#646478');oval((17,22,35,35),'#BDD1DD');oval((38,22,57,35),'#BDD1DD');line((36,27,36,13),'#5B5D69',2)
        arc((8,8,64,64),10,350,'#D46D67',4);line((17,57,55,17),'#D46D67',4)
    elif key=='mop':line((42,9,31,47),'#8DABBB',5);poly([(21,43),(42,46),(53,62),(11,62)],'#83BDB5')
    elif key=='comb':
        box((10,18,62,29),'#B48B65',r=4)
        for x in range(13,62,5):box((x,25,x+2,53),'#B48B65',r=1)
    elif key in ['toothpaste','lip-balm','glue','highlighter']:
        colors={'toothpaste':'#8ABBC9','lip-balm':'#C98A9D','glue':'#C8AD67','highlighter':'#DBDB64'}
        box((25,14,48,56),colors[key],r=3);box((26,56,47,64),'#6D7D8F',r=2);box((29,7,44,15),'#A7B9C0',r=2)
        if key=='toothpaste':line((30,24,43,37),'#F5FAFC',4)
    elif key=='nail-cutter':
        poly([(19,56),(40,17),(50,23),(29,62)],'#A0B7C3');poly([(24,23),(49,17),(52,26),(27,32)],'#CDDAE0');oval((37,24,41,28),'#718C9E')
    elif key=='diaper':
        poly([(13,20),(59,20),(54,47),(44,61),(28,61),(18,47)],'#C8E0EC');oval((22,43,50,66),'#F4FAFC');box((13,22,26,32),'#83B6CF',r=3);box((46,22,59,32),'#83B6CF',r=3)
    elif key=='kurta':poly([(24,12),(13,20),(7,42),(19,46),(24,32),(24,63),(49,63),(49,32),(54,46),(66,42),(60,20),(47,12),(40,20),(32,20)],'#81A9BF');line((36,20,36,37),'#E1EEF3',2)
    elif key=='belt':box((8,29,64,44),'#986D48',r=3);box((11,26,29,47),'#D8C797','#A49165',r=2);box((16,30,25,43),'#986D48');line((20,36,33,36),'#E0D1A4',2)
    elif key=='wallet':box((10,19,62,58),'#9F754E',r=6);box((42,31,66,47),'#BA9062',r=4);oval((50,36,55,41),'#E7CCA0')
    elif key in ['fan','fridge','washing-machine','microwave','air-conditioner','iron','blender','toaster','vacuum','water-filter','pressure-cooker']:
        if key=='fan':
            for a in [0,120,240]:
                rad=math.radians(a);x=36+17*math.cos(rad);y=34+17*math.sin(rad);oval((x-12,y-8,x+12,y+8),'#88AEBE')
            oval((29,27,43,41),'#CBDDE4');line((36,41,36,61),'#829AA9',4);box((21,60,51,65),'#829AA9',r=3)
        elif key=='iron':poly([(10,53),(17,39),(43,21),(58,32),(63,53)],'#92B9CF');box((31,21,52,33),'#E4EFF3',r=4);line((8,54,65,54),'#6A889E',4)
        elif key=='blender':
            poly([(22,10),(53,10),(49,45),(27,45)],'#C6DFE6');box((24,45,51,65),'#8BAEBA',r=4);box((20,7,55,13),'#6D8D9E',r=3);arc((44,16,64,36),270,90,'#7D9CAB',4)
        elif key=='pressure-cooker':
            box((15,27,57,59),'#B7CAD4',r=9);oval((13,19,59,32),'#DDE7EC');box((31,12,42,22),'#667F8F',r=3);box((7,31,18,39),'#667F8F',r=2);box((55,31,67,39),'#667F8F',r=2)
        elif key=='vacuum':
            oval((9,40,45,64),'#B29BAF');oval((16,55,28,67),'#617C8D');arc((31,11,60,54),170,340,'#7694A6',5);line((61,16,55,58),'#7694A6',4);box((47,58,67,64),'#607C8D',r=2)
        else:
            wide=key in ['microwave','air-conditioner','toaster'];xy=(7,20,65,56) if wide else (17,9,56,65)
            box(xy,'#B8CFDC','#809DAC',r=5)
            if key=='fridge':line((18,30,55,30),'#6E8B9B',2);box((44,16,48,26),'#718E9F',r=1);box((44,37,48,52),'#718E9F',r=1)
            elif key=='washing-machine':oval((23,29,50,57),'#7C9DAD');oval((27,33,46,53),'#B8DDE5');oval((24,16,29,21),'#6A8F9E')
            elif key=='microwave':box((12,25,48,50),'#577789',r=3);oval((54,30,59,35),'#718E9C');oval((54,41,59,46),'#718E9C')
            elif key=='air-conditioner':
                for y in [36,42,48]:line((14,y,59,y),'#7796A7',2)
            elif key=='toaster':box((14,13,56,27),'#D8AF7A','#A87F52',r=7);line((19,26,52,26),'#748D9B',3)
            else:box((26,19,47,42),'#80BCD5',r=4);box((31,46,43,52),'#7193A6',r=2);line((41,52,47,52),'#7193A6',3)
    elif key in ['calculator','geometry-box','drawing-book','eraser','sharpener','memory-card','power-bank','usb-drive','printer-ink','earbuds','extension','cable','jug','container','foil','cling-film','matches']:
        if key=='eraser':poly([(12,45),(29,19),(58,37),(43,61)],'#D5A1B0');poly([(12,45),(23,29),(50,48),(43,61)],'#8DADBF')
        elif key=='sharpener':box((15,20,58,57),'#A0BDCF',r=4);box((29,25,44,52),'#DCE5E9',r=3);oval((33,33,40,40),'#7C929C')
        elif key=='drawing-book':box((15,11,58,64),'#9BC5CB',r=3);box((23,22,52,53),'#F6EFE0');poly([(25,49),(36,31),(43,42),(48,36),(52,49)],'#8AAF75');oval((42,25,49,32),'#E4B966')
        elif key=='calculator':
            box((17,9,55,65),'#7E9DAE',r=5);box((23,16,49,28),'#D6E5CD',r=2)
            for x in [24,34,44]:
                for y in [35,44,53]:box((x,y,x+5,y+5),'#D3E0E7',r=1)
        elif key=='geometry-box':box((9,23,63,56),'#93AAC8',r=4);poly([(18,47),(41,28),(41,47)],'#E7D6A8');line((46,30,53,48),'#E6DFD0',2)
        elif key=='memory-card':poly([(19,16),(47,16),(55,26),(55,63),(19,63)],'#687E90');box((24,34,49,53),'#CDD9E0',r=2);[box((23+i*6,17,26+i*6,28),'#DEBE73') for i in range(4)]
        elif key=='power-bank':box((19,13,54,62),'#8CA7BB',r=5);[oval((25+i*6,21,28+i*6,24),'#D4ECCB') for i in range(4)];poly([(38,30),(28,45),(36,45),(32,55),(46,39),(38,39)],'#F1D592')
        elif key=='usb-drive':box((25,29,48,63),'#7A9BAF',r=5);box((28,12,45,30),'#CFD9DE');box((31,17,35,22),'#8A9BA5');box((38,17,42,22),'#8A9BA5')
        elif key=='printer-ink':box((18,17,54,59),'#647D8F',r=4);box((22,9,50,19),'#B2C7D2',r=2);[box((24+i*8,31,29+i*8,49),c,r=1) for i,c in enumerate(['#68B4CF','#C8769D','#D5BD55'])]
        elif key=='earbuds':
            box((11,37,61,65),'#C7DDE5',r=10)
            for x in [23,43]:oval((x-5,10,x+8,23),'#B3CBD9');box((x,17,x+5,39),'#B3CBD9',r=2)
        elif key=='extension':
            box((6,28,66,48),'#D5E1E6','#91AAB8',r=5)
            for x in [18,35,52]:oval((x-5,32,x+5,43),'#AEC2CD');oval((x-2,35,x-1,38),'#607C90');oval((x+2,35,x+3,38),'#607C90')
        elif key=='cable':arc((14,17,57,60),30,320,'#7898AA',5);box((11,10,22,26),'#ACCAD8',r=3);box((51,42,62,59),'#ACCAD8',r=3)
        elif key=='jug':poly([(20,17),(50,17),(52,61),(17,61)],'#A6CFDE');arc((40,21,66,51),270,90,'#83B3C6',5);poly([(18,17),(9,17),(19,28)],'#A6CFDE')
        elif key=='container':box((11,25,61,62),'#BEDBD9',r=5);box((8,19,64,28),'#7DACA7',r=3)
        elif key in ['foil','cling-film']:box((10,26,62,51),'#AFC2CB' if key=='foil' else '#C3D7C9',r=3);poly([(20,26),(29,12),(61,12),(54,26)],'#DFE7EC' if key=='foil' else '#DCEBE0');oval((6,26,18,51),'#7895A4')
        elif key=='matches':box((10,22,62,59),'#BD825B',r=3);box((15,27,57,54),'#E1BC83');line((28,44,44,13),'#BBA074',4);oval((40,9,49,19),'#BA6152')
    else:
        raise ValueError('No hand-drawn illustration: '+key)
    return im.resize((72,72),Image.Resampling.LANCZOS)

if __name__=='__main__':
    import re
    source=(DEST.parents[1]/'java/com/sadique/dailyledger/data/ItemArtwork.kt').read_text()
    keys=set(re.findall(r' to "([a-z0-9-]+)"',source))
    originals=sorted(k for k in keys if not re.fullmatch('[0-9a-f]{4,6}',k))
    DEST.mkdir(parents=True,exist_ok=True)
    for key in originals:render(key).save(DEST/(key+'.png'),optimize=True)
    print('Rendered original pictograms:',len(originals))
