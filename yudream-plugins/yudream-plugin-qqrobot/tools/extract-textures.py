"""Pack vanilla item/block textures from a Minecraft client jar into one zip.

Usage:
    python tools/extract-textures.py <client.jar> <out.zip>

The server plugin indexes CraftEngine / resource-pack textures at runtime; this
archive is only the admin-side fallback so previews render real icons without a
connected server. Rebuild it when bumping the target Minecraft version.
"""
import sys
import zipfile

SOURCE_PREFIX = "assets/minecraft/textures/"
KINDS = ("item", "block")


def main(source, target):
    count = 0
    with zipfile.ZipFile(source) as client, zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as out:
        for entry in client.infolist():
            if not entry.filename.startswith(SOURCE_PREFIX):
                continue
            relative = entry.filename[len(SOURCE_PREFIX):]
            kind, _, name = relative.partition("/")
            if kind not in KINDS or "/" in name or not name.endswith(".png"):
                continue
            out.writestr(relative, client.read(entry))
            count += 1
    print(f"packed {count} textures into {target}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
