from PIL import Image
import os

src = r"C:\Users\30797\WorkBuddy\2026-08-11-15-45-21\suanbao-android\suanbao-thinking.png"
dst = src  # overwrite

img = Image.open(src)
if img.mode != 'RGBA':
    img = img.convert('RGBA')

datas = img.getdata()
new_data = []
for item in datas:
    # White or near-white background -> transparent
    r, g, b, a = item
    if r > 230 and g > 230 and b > 230:
        new_data.append((255, 255, 255, 0))
    else:
        new_data.append(item)

img.putdata(new_data)
img.save(dst, 'PNG')
print(f"Done: {dst}")
print(f"Size: {img.size}, Mode: {img.mode}")
