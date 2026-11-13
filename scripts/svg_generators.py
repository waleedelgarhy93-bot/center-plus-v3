import urllib.parse

def make_stem_leaf_svg(title, rows, key_text, width=320, height=200):
    """
    rows: list of tuples (stem_str, leaves_list_of_strs)
    """
    line_count = len(rows)
    h = max(height, 80 + line_count * 30 + 40)
    
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {h}" width="100%" height="{h}" style="background:#ffffff; border-radius:12px; font-family:sans-serif;">
      <!-- Title -->
      <rect x="0" y="0" width="{width}" height="36" fill="#f3f4f6" rx="12 12 0 0"/>
      <text x="{width/2}" y="23" font-size="14" font-weight="bold" fill="#374151" text-anchor="middle">{title}</text>
      
      <!-- Table Headers -->
      <text x="75" y="60" font-size="13" font-weight="bold" fill="#6b21a8" text-anchor="middle">الساق</text>
      <text x="190" y="60" font-size="13" font-weight="bold" fill="#1e40af" text-anchor="middle">الأوراق</text>
      <line x1="20" y1="68" x2="{width-20}" y2="68" stroke="#e5e7eb" stroke-width="1.5" />
      <line x1="120" y1="45" x2="120" y2="{h-35}" stroke="#9333ea" stroke-width="2" stroke-linecap="round"/>
    '''
    
    y = 92
    for stem, leaves in rows:
        leaves_str = "    ".join(leaves)
        svg += f'''
      <text x="75" y="{y}" font-size="15" font-weight="bold" fill="#111827" text-anchor="middle">{stem}</text>
      <text x="140" y="{y}" font-size="15" font-weight="600" fill="#1f2937" text-anchor="start" letter-spacing="4">{leaves_str}</text>
        '''
        y += 28

    svg += f'''
      <!-- Key Box -->
      <rect x="20" y="{h-32}" width="{width-40}" height="24" fill="#faf5ff" stroke="#e9d5ff" rx="6" />
      <text x="{width/2}" y="{h-16}" font-size="11" font-weight="bold" fill="#7e22ce" text-anchor="middle">المفتاح: {key_text}</text>
    </svg>'''
    return f"data:image/svg+xml;utf8,{urllib.parse.quote(svg)}"

def make_back_to_back_svg(title, rows, key_text, width=380, height=220):
    """
    Back-to-back stem-leaf for Group A & Group B
    rows: list of tuples (leaves_A_list, stem_str, leaves_B_list)
    """
    line_count = len(rows)
    h = max(height, 80 + line_count * 30 + 40)
    
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {h}" width="100%" height="{h}" style="background:#ffffff; border-radius:12px; font-family:sans-serif;">
      <!-- Title -->
      <rect x="0" y="0" width="{width}" height="36" fill="#f3f4f6" rx="12 12 0 0"/>
      <text x="{width/2}" y="23" font-size="14" font-weight="bold" fill="#374151" text-anchor="middle">{title}</text>
      
      <!-- Table Headers -->
      <text x="80" y="60" font-size="12" font-weight="bold" fill="#dc2626" text-anchor="middle">المجموعة (أ)</text>
      <text x="{width/2}" y="60" font-size="13" font-weight="bold" fill="#6b21a8" text-anchor="middle">الساق</text>
      <text x="{width - 80}" y="60" font-size="12" font-weight="bold" fill="#2563eb" text-anchor="middle">المجموعة (ب)</text>
      
      <line x1="20" y1="68" x2="{width-20}" y2="68" stroke="#e5e7eb" stroke-width="1.5" />
      <!-- Vertical dividers -->
      <line x1="{width/2 - 35}" y1="45" x2="{width/2 - 35}" y2="{h-35}" stroke="#9333ea" stroke-width="2" stroke-linecap="round"/>
      <line x1="{width/2 + 35}" y1="45" x2="{width/2 + 35}" y2="{h-35}" stroke="#9333ea" stroke-width="2" stroke-linecap="round"/>
    '''
    
    y = 92
    for leaves_a, stem, leaves_b in rows:
        str_a = "    ".join(leaves_a)
        str_b = "    ".join(leaves_b)
        svg += f'''
      <text x="{width/2 - 45}" y="{y}" font-size="14" font-weight="600" fill="#991b1b" text-anchor="end">{str_a}</text>
      <text x="{width/2}" y="{y}" font-size="15" font-weight="bold" fill="#111827" text-anchor="middle">{stem}</text>
      <text x="{width/2 + 45}" y="{y}" font-size="14" font-weight="600" fill="#1e40af" text-anchor="start">{str_b}</text>
        '''
        y += 28

    svg += f'''
      <!-- Key Box -->
      <rect x="20" y="{h-32}" width="{width-40}" height="24" fill="#faf5ff" stroke="#e9d5ff" rx="6" />
      <text x="{width/2}" y="{h-16}" font-size="11" font-weight="bold" fill="#7e22ce" text-anchor="middle">المفتاح: {key_text}</text>
    </svg>'''
    return f"data:image/svg+xml;utf8,{urllib.parse.quote(svg)}"
