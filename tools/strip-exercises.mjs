// Builds app/src/main/assets/exercises.json from free-exercise-db.
//
// Source: https://github.com/yuhonas/free-exercise-db (dist/exercises.json),
// released under the Unlicense (public domain), itself restructured from
// https://github.com/wrkout/exercises.json (also Unlicense).
//
// The app ships without exercise images, so `images` is dropped; every other
// field is kept as-is so the asset stays easy to diff against upstream.
//
// Usage (Node 18+):
//   curl -L -o /tmp/exercises.json https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json
//   node tools/strip-exercises.mjs /tmp/exercises.json
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const src = process.argv[2];
if (!src) {
  console.error("usage: node tools/strip-exercises.mjs <upstream exercises.json>");
  process.exit(1);
}

const KEEP = ["id", "name", "force", "level", "mechanic", "equipment",
  "primaryMuscles", "secondaryMuscles", "instructions", "category"];

const upstream = JSON.parse(readFileSync(src, "utf8"));
const seen = new Set();
const out = upstream
  .map(e => Object.fromEntries(KEEP.map(k => [k, e[k] ?? null])))
  .filter(e => {
    if (!e.id || !e.name || seen.has(e.id)) return false;
    seen.add(e.id);
    return true;
  })
  .sort((a, b) => a.id.localeCompare(b.id));

const dest = join(dirname(fileURLToPath(import.meta.url)), "..", "app", "src", "main", "assets", "exercises.json");
writeFileSync(dest, JSON.stringify(out));
console.log(`${out.length} exercises → ${dest}`);
