from PIL import Image

src = r"C:\Users\30797\WorkBuddy\2026-08-11-15-45-21\suanbao-android\suanbao-thinking.png"
img = Image.open(src)

# Auto-crop: find bounding box of non-transparent pixels
bbox = img.getbbox()
print(f"Original size: {img.size}, BBox: {bbox}")

if bbox:
    img = img.crop(bbox)
    print(f"Cropped size: {img.size}")

# Resize to match mascot proportions (512x597 -> keep aspect ratio, fit within similar box)
target_w = 512
target_h = 597
img_resized = img.resize((target_w, target_h), Image.LANCZOS)
print(f"Resized to: {img_resized.size}")

img_resized.save(src, 'PNG')
print("Saved.")
