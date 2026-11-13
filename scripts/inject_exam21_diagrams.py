import os
from dotenv import load_dotenv
load_dotenv('F:/مشروعات saas/Lopital center/center-saas/.env')
from supabase import create_client
from svg_generators import make_stem_leaf_svg, make_back_to_back_svg

sb = create_client(os.getenv('SUPABASE_URL'), os.getenv('SUPABASE_SECRET_KEY'))

# 1. Diagram for Q215, 216: Values: 23, 27, 30, 35, 38, 42 (Min: 23, Max: 42)
img_215_216 = make_stem_leaf_svg(
    "مخطط الساق والأوراق",
    [
        ("٢", ["٣", "٧"]),
        ("٣", ["٠", "٥", "٨"]),
        ("٤", ["٢"])
    ],
    "٢ | ٣ = ٢٣"
)

# 2. Diagram for Q217, 218, 219: Values: 14, 18, 24, 24, 24, 27, 30, 31 (Total: 8, Range: 31-14=17, Mode: 24)
img_217_218_219 = make_stem_leaf_svg(
    "مخطط الساق والأوراق",
    [
        ("١", ["٤", "٨"]),
        ("٢", ["٤", "٤", "٤", "٧"]),
        ("٣", ["٠", "١"])
    ],
    "١ | ٤ = ١٤"
)

# 3. Diagram for Q222: Values: 17, 20, 23, 25, 28, 31, 35 (Count: 7, Median: 25)
img_222 = make_stem_leaf_svg(
    "مخطط الساق والأوراق",
    [
        ("١", ["٧"]),
        ("٢", ["٠", "٣", "٥", "٨"]),
        ("٣", ["١", "٥"])
    ],
    "٢ | ٠ = ٢٠"
)

# 4. Diagram for Q223: Even count median: 48, 50, 51, 53, 56, 58 (Median: (51+53)/2 = 52)
img_223 = make_stem_leaf_svg(
    "مخطط الساق والأوراق (أطوال بالسنتيمتر)",
    [
        ("٤", ["٨"]),
        ("٥", ["٠", "١", "٣", "٦", "٨"])
    ],
    "٥ | ١ = ٥١ سم"
)

# 5. Diagram for Q224: Mean = 20: Values: 15, 18, 20, 22, 25 (Sum: 100 / 5 = 20)
img_224 = make_stem_leaf_svg(
    "مخطط الساق والأوراق",
    [
        ("١", ["٥", "٨"]),
        ("٢", ["٠", "٢", "٥"])
    ],
    "١ | ٥ = ١٥"
)

# 6. Diagram for Q225: Scores out of 50. Count >= 35 is 5.
# Values: 22, 28, 31, 34, 35, 38, 42, 45, 49 (>= 35 are: 35, 38, 42, 45, 49 -> 5 students)
img_225 = make_stem_leaf_svg(
    "درجات اختبار الطلاب (من ٥٠ درجة)",
    [
        ("٢", ["٢", "٨"]),
        ("٣", ["١", "٤", "٥", "٨"]),
        ("٤", ["٢", "٥", "٩"])
    ],
    "٣ | ٥ = ٣٥ درجة"
)

# 7. Diagram for Q226: Double stem-leaf for cities A & B. City A range = 22.
# City A: 16, 18, 24, 32, 38 (Range: 38 - 16 = 22)
# City B: 19, 21, 25, 29, 35 (Range: 35 - 19 = 16)
img_226 = make_back_to_back_svg(
    "مخطط درجات الحرارة لمدينتين (أ) و (ب)",
    [
        (["٨", "٦"], "١", ["٩"]),
        (["٤"], "٢", ["١", "٥", "٩"]),
        (["٨", "٢"], "٣", ["٥"])
    ],
    "أ: ٦ | ١ = ١٦°م  /  ب: ١ | ٩ = ١٩°م"
)

# 8. Diagram for Q227: Range = 34, Min = 13, Max = 40 + s => 4s - 13 = 34 => 4s = 47 => s = 7.
img_227 = make_stem_leaf_svg(
    "مخطط الساق والأوراق مع مجهول",
    [
        ("١", ["٣", "٦"]),
        ("٢", ["١", "٥", "٨"]),
        ("٣", ["٢", "٧"]),
        ("٤", ["٠", "s"])
    ],
    "١ | ٣ = ١٣"
)

# 9. Diagram for Q228: Median = 36. Values: 24, 28, 32, 30+ر, 41, 45, 48.
# Median is the 4th item => 30 + ر = 36 => ر = 6 (Or sorted where median = 36, correct ans is 8 => 32, 35, 36/ر)
# Ans in DB for 228 is: 8. So if ر = 8, median = 36: Values: 25, 31, 36, 38(ر), 42, 45.
img_228 = make_stem_leaf_svg(
    "مخطط الساق والأوراق مع مجهول",
    [
        ("٢", ["٥", "٩"]),
        ("٣", ["٢", "٦", "ر"]),
        ("٤", ["١", "٧"])
    ],
    "٣ | ٦ = ٣٦"
)

# 10. Diagram for Q229: Double plot, difference between Medians is 3.
# Group A median = 25, Group B median = 22 => Diff = 3.
img_229 = make_back_to_back_svg(
    "مخطط درجات مجموعتين (أ) و (ب)",
    [
        (["٧"], "١", ["٥", "٨"]),
        (["٩", "٥", "١"], "٢", ["٢", "٦"]),
        (["٤"], "٣", ["٠"])
    ],
    "المفتاح: ٢ | ٥ = ٢٥"
)

# 11. Diagram for Q230: Leaves m & r, m + r = 11.
img_230 = make_stem_leaf_svg(
    "مخطط الساق والأوراق بالأمتار",
    [
        ("٢", ["١", "m"]),
        ("٣", ["٢", "ر", "٦"]),
        ("٤", ["٩"])
    ],
    "٢ | ١ = ٢.١ م"
)

# 12. Diagram for Q231: Group A vs Group B dispersion comparison.
# Group A has wider spread (Range: 48 - 12 = 36), Group B (Range: 32 - 20 = 12).
img_231 = make_back_to_back_svg(
    "مقارنة تشتت درجات مجموعتين",
    [
        (["٥", "٢"], "١", []),
        (["٨", "٠"], "٢", ["٠", "٣", "٦"]),
        (["٤"], "٣", ["٢"]),
        (["٨", "١"], "٤", [])
    ],
    "المفتاح: ٢ | ٠ = ٢٠"
)

updates = [
    (215, img_215_216),
    (216, img_215_216),
    (217, img_217_218_219),
    (218, img_217_218_219),
    (219, img_217_218_219),
    (222, img_222),
    (223, img_223),
    (224, img_224),
    (225, img_225),
    (226, img_226),
    (227, img_227),
    (228, img_228),
    (229, img_229),
    (230, img_230),
    (231, img_231)
]

print("Updating questions in database...")
for q_id, img_url in updates:
    res = sb.table('questions').update({'question_image_url': img_url}).eq('id', q_id).execute()
    print(f"Updated Q {q_id}: success={bool(res.data)}")

print("All diagrams injected successfully!")
