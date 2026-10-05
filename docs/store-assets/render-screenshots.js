// Renders the Play Store phone screenshots (1080x1920) and the feature
// graphic (1024x500): a headline over the app screen in a phone frame, on
// Fernday's navy and green. Uses headless Chrome, so no npm install.
//
// Run:
//   node docs/store-assets/render-screenshots.js <raw-dir>
// <raw-dir> holds 1080x2400 captures named after SLIDES[].raw (see
// docs/ASSETS.md for how they were taken). Output:
//   docs/store-assets/screenshots/NN_<name>.png
//   docs/store-assets/feature-1024x500.png
// CHROME=<path to chrome.exe> overrides the default install path.

const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync } = require("child_process");

const CHROME = process.env.CHROME || "C:/Program Files/Google/Chrome/Application/chrome.exe";
const ROOT = path.resolve(__dirname, "..", "..");
const FONT = path.join(ROOT, "app/src/main/assets/fonts/plus_jakarta_sans.ttf");
const OUT = path.join(__dirname, "screenshots");
const raw = process.argv[2];
if (!raw) { console.error("usage: node render-screenshots.js <raw-dir>"); process.exit(1); }

const fileUrl = (p) => "file:///" + path.resolve(p).replace(/\\/g, "/");

// Order is the listing order. <em> marks the accent words.
const SLIDES = [
  { name: "score",       raw: "home.png",        brand: true,
    title: "Your whole day in <em>one calm score</em>",
    sub: "Water, steps, sleep, mood and food, together." },
  { name: "checkin",     raw: "checkin.png",
    title: "Check in each evening in <em>under a minute</em>",
    sub: "Then see what every part of your day earned." },
  { name: "food",        raw: "food.png",
    title: "Log meals <em>your way</em>",
    sub: "Quality tags, Nutri-Score and optional calories." },
  { name: "food_search", raw: "food_search.png",
    title: "Search, scan or <em>pick a favorite</em>",
    sub: "Look up foods by name or barcode." },
  { name: "workouts",    raw: "move.png",
    title: "Workouts for <em>every level</em>",
    sub: "10 ready-made routines, or build your own." },
  { name: "player",      raw: "player.png",      full: true,   // the rest timer is at the bottom
    title: "Train <em>set by set</em>",
    sub: "Last time's numbers and a rest timer, right there." },
  { name: "records",     raw: "records.png",
    title: "Celebrate <em>new records</em>",
    sub: "Heaviest lift, best 1-rep max, most reps, longest hold." },
  { name: "insights",    raw: "insights.png",
    title: "Watch your <em>trends grow</em>",
    sub: "Weekly averages, goals and streaks." },
];

const HEART = `<svg viewBox="0 0 24 24" width="SIZE" height="SIZE"><path fill="#1AD9A0" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>`;

const BASE_CSS = `
  @font-face { font-family: Jakarta; src: url("${fileUrl(FONT)}"); font-weight: 200 800; }
  html, body { margin: 0; overflow: hidden; background: #070D1A; font-family: Jakarta, sans-serif; }
  em { font-style: normal; background: linear-gradient(90deg, #1AD9A0, #5EC4FF);
       -webkit-background-clip: text; background-clip: text; color: transparent; }
  .phone { position: absolute; background: #0A0E16; box-sizing: border-box;
           box-shadow: 0 0 0 3px #2B3650, 0 40px 120px rgba(0,0,0,.6), 0 0 160px rgba(26,217,160,.16); }
  .phone img { display: block; width: 100%; }
  .hole { position: absolute; left: 50%; border-radius: 50%; background: #000; }
`;

function slideHtml(s) {
  return `<!doctype html><html><head><meta charset="utf-8"><style>${BASE_CSS}
  html, body { width: 1080px; height: 1920px; }
  .bg { position: absolute; inset: 0; background:
      radial-gradient(900px 760px at 50% 82%, rgba(26,217,160,.20), transparent 70%),
      radial-gradient(700px 520px at 92% 6%, rgba(167,139,250,.20), transparent 70%),
      radial-gradient(640px 520px at 4% 14%, rgba(94,196,255,.13), transparent 70%),
      linear-gradient(180deg, #0D1730 0%, #070D1A 100%); }
  .stage { position: absolute; top: ${s.brand ? 96 : 120}px; left: 0; right: 0;
           display: flex; flex-direction: column; align-items: center; }
  .top { padding: 0 70px; text-align: center; }
  .brand { display: inline-flex; align-items: center; gap: 14px; font-weight: 800; font-size: 44px;
           color: #fff; letter-spacing: -.5px; margin-bottom: 30px; }
  h1 { margin: 0; font-weight: 800; font-size: 82px; line-height: 1.08; letter-spacing: -1.6px; color: #fff;
       text-wrap: balance; }
  p { margin: 26px 0 0; font-weight: 500; font-size: 38px; line-height: 1.3; color: #A9B8CC; text-wrap: balance; }
  .stage .phone { position: relative; margin-top: 78px; width: 820px; flex: none;
           border-radius: 96px; padding: 18px; }
  .phone img { border-radius: 80px; }
  .stage .phone.full { width: 640px; border-radius: 76px; padding: 14px; }
  .phone.full img { border-radius: 64px; }
  .phone.full .hole { top: 36px; width: 24px; height: 24px; margin-left: -12px; }
  .hole { top: 46px; width: 30px; height: 30px; margin-left: -15px; box-shadow: 0 0 0 4px #10141D; }
  </style></head><body>
  <div class="bg"></div>
  <div class="stage">
    <div class="top">
      ${s.brand ? `<div class="brand">${HEART.replace(/SIZE/g, "52")}Fernday</div>` : ""}
      <h1>${s.title}</h1><p>${s.sub}</p>
    </div>
    <div class="phone${s.full ? " full" : ""}"><img src="${fileUrl(path.join(raw, s.raw))}"><div class="hole"></div></div>
  </div>
  </body></html>`;
}

function featureHtml() {
  return `<!doctype html><html><head><meta charset="utf-8"><style>${BASE_CSS}
  html, body { width: 1024px; height: 500px; }
  .bg { position: absolute; inset: 0; background:
      radial-gradient(520px 420px at 78% 70%, rgba(26,217,160,.22), transparent 70%),
      radial-gradient(420px 300px at 8% 0%, rgba(94,196,255,.14), transparent 70%),
      linear-gradient(135deg, #0D1730 0%, #070D1A 100%); }
  .text { position: absolute; left: 68px; top: 128px; width: 520px; }
  .brand { display: flex; align-items: center; gap: 16px; font-weight: 800; font-size: 86px;
           color: #fff; letter-spacing: -2px; line-height: 1; }
  p { margin: 26px 0 0; font-weight: 500; font-size: 27px; line-height: 1.4; color: #A9B8CC; }
  .phone { width: 250px; border-radius: 34px; padding: 7px; }
  .phone img { border-radius: 28px; }
  .hole { top: 17px; width: 11px; height: 11px; margin-left: -5.5px; }
  .back  { left: 610px; top: 92px;  transform: rotate(-9deg); opacity: .9; }
  .front { left: 735px; top: 58px;  transform: rotate(6deg); }
  </style></head><body>
  <div class="bg"></div>
  <div class="text">
    <div class="brand">${HEART.replace(/SIZE/g, "74")}Fernday</div>
    <p>A calm daily wellness tracker.<br>Meals, workouts, steps and sleep.</p>
  </div>
  <div class="phone back"><img src="${fileUrl(path.join(raw, "insights.png"))}"><div class="hole"></div></div>
  <div class="phone front"><img src="${fileUrl(path.join(raw, "home.png"))}"><div class="hole"></div></div>
  </body></html>`;
}

function render(html, out, w, h) {
  const tmp = path.join(os.tmpdir(), `fernday-${path.basename(out, ".png")}.html`);
  fs.writeFileSync(tmp, html);
  execFileSync(CHROME, [
    "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
    "--allow-file-access-from-files", `--window-size=${w},${h}`, `--screenshot=${out}`, fileUrl(tmp),
  ], { stdio: "ignore" });
  console.log(`✔ ${path.relative(ROOT, out)} (${w}x${h})`);
}

fs.mkdirSync(OUT, { recursive: true });
SLIDES.forEach((s, i) =>
  render(slideHtml(s), path.join(OUT, `${String(i + 1).padStart(2, "0")}_${s.name}.png`), 1080, 1920));
render(featureHtml(), path.join(__dirname, "feature-1024x500.png"), 1024, 500);
