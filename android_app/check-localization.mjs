import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));
const resourceDir = path.join(root, "app", "src", "main", "res");

function readStrings(relativePath) {
  const file = path.join(resourceDir, relativePath);
  const source = fs.readFileSync(file, "utf8");
  const values = new Map();
  const duplicates = [];
  const pattern = /<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g;
  for (const match of source.matchAll(pattern)) {
    const [, name, value] = match;
    if (values.has(name)) duplicates.push(name);
    values.set(name, value);
  }
  return { file, values, duplicates };
}

function formatTokens(value) {
  return [...value.matchAll(/%(?:\d+\$)?[-+0-9.#]*[a-zA-Z%]/g)].map((match) => match[0]).sort();
}

const english = readStrings(path.join("values", "strings.xml"));
const hindi = readStrings(path.join("values-hi", "strings.xml"));
const marathi = readStrings(path.join("values-mr", "strings.xml"));
const failures = [];

for (const [locale, resource] of [["Hindi", hindi], ["Marathi", marathi]]) {
  for (const name of english.values.keys()) {
    if (!resource.values.has(name)) failures.push(`${locale} is missing ${name}`);
    else if (JSON.stringify(formatTokens(english.values.get(name))) !== JSON.stringify(formatTokens(resource.values.get(name)))) {
      failures.push(`${locale} changed format placeholders for ${name}`);
    }
  }
  for (const name of resource.values.keys()) {
    if (!english.values.has(name)) failures.push(`${locale} defines unknown key ${name}`);
  }
  for (const name of resource.duplicates) failures.push(`${locale} defines duplicate key ${name}`);
}

if (english.duplicates.length) {
  for (const name of english.duplicates) failures.push(`English defines duplicate key ${name}`);
}

if (failures.length) {
  console.error("Localization check failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(`Localization check passed: ${english.values.size} English, ${hindi.values.size} Hindi, ${marathi.values.size} Marathi keys.`);
