# Item artwork and category colors

The catalog now contains 609 built-in labels, including 322 additional everyday
items. `ExpandedItemCategories` adds concrete products with English, Roman Urdu
and Urdu aliases. The picker, offline AI rules, online prompt and validation share
these categories. Existing transactions are not migrated or renamed.
Door/darwaza/دروازہ, Rickshaw/rikshaw/رکشہ and Conveyance now have dedicated
artwork, alongside 108 other additions covering fittings, transport, hardware,
electrical parts, bathroom and kitchen tools, sewing supplies, stationery and
gardening. Recognized existing entry notes use the same bundled images.

`ItemArtwork` assigns the catalog 451 offline pictograms: 180 unmodified Twemoji
14.0.2 illustrations and 271 original DailyLedger illustrations. Assets total
about 831 KiB before APK compression. Aliases such as Peanuts/moongphali/مونگ پھلی
and Curtains/parday/پردے use the same picture. `tools/generate-item-art.py`
and `tools/item_art_everyday.py` recreate the original art with Pillow;
downloaded Twemoji originals and their
CC BY 4.0 license are retained in assets. Attribution is visible in App info.

`ItemCategoryIcon` is shared by category dropdowns/dialogs, AI draft review,
dashboard frequent/recent items and transaction history. An explicitly chosen
icon wins, then curated item artwork (including a recognized entry item name),
then a downloaded custom-item image, then the existing vector fallback. Known
items are excluded from web image backfill so an unrelated search result cannot
replace their curated artwork. Unrecognized future items retain the fallback.

`CategoryChartColors` assigns deterministic opaque colors for the current category set,
using perceptual distance in OKLab for the first 64 colors and collision detection
for additional labels. Colors remain stable when spending order changes; adding
or removing a category can rebalance the palette. Donut slices, bar view, legend and selected-slice detail use
the same mapping; there is no eight-color modulo reuse or theme tint that can
collapse distinct colors. Tests cover 10,000 distinct labels and reordering.
Distinct RGB values do not imply 10,000 perceptually distinguishable shades;
labels and percentages remain available for interpretation.

Backup transport, format, storage, scheduling and restore behavior are unchanged.
This is a shared vocabulary/prompt update, not model weight training. Live Online
AI requests still require network access and enabled Cloud AI.
