// Renders the Play Store app icon from its SVG source. The feature graphic
// and screenshots come from render-screenshots.js.
// Run:
//   cd docs/store-assets && npm install sharp && node render.js
// Output:
//   docs/store-assets/icon-512.png         (Play Store app icon)

const fs = require("fs");
const path = require("path");
const sharp = require("sharp");

async function render(svgFile, pngFile, width, height) {
  const svg = fs.readFileSync(path.join(__dirname, svgFile));
  await sharp(svg, { density: 384 })
    .resize(width, height, { fit: "fill" })
    .png({ compressionLevel: 9 })
    .toFile(path.join(__dirname, pngFile));
  console.log(`✔ ${pngFile} (${width}x${height})`);
}

(async () => {
  await render("icon-512.svg",          "icon-512.png",          512,  512);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
