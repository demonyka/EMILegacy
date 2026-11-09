// Downloads the latest Minecraft 1.16.5 Forge release of each CurseForge / Modrinth mod into a folder.
// Usage: node tools/fetch-mods.js <dest dir> <slug>... (prefix "mr:" for Modrinth, default CurseForge)
// CurseForge metadata comes from api.cfwidget.com (no API key needed).
const fs = require("fs");
const path = require("path");

const [dest, ...slugs] = process.argv.slice(2);
fs.mkdirSync(dest, { recursive: true });

async function json(url) {
	const r = await fetch(url, { headers: { "User-Agent": "emi-legacy-compat-test" } });
	if (!r.ok) throw new Error(url + " -> " + r.status);
	return r.json();
}

async function download(url, file) {
	const r = await fetch(url, { headers: { "User-Agent": "emi-legacy-compat-test" } });
	if (!r.ok) throw new Error(url + " -> " + r.status);
	fs.writeFileSync(file, Buffer.from(await r.arrayBuffer()));
}

async function curseforge(slug) {
	const j = await json("https://api.cfwidget.com/minecraft/mc-mods/" + slug);
	const files = j.files.filter((f) => f.versions.includes("1.16.5") && !f.versions.includes("Fabric"))
		.sort((a, b) => (a.type === "release") - (b.type === "release") || a.id - b.id).reverse();
	const f = files.find((x) => x.type === "release") || files[0];
	if (!f) throw new Error("no 1.16.5 file");
	const id = String(f.id);
	const url = `https://mediafilez.forgecdn.net/files/${id.slice(0, 4)}/${Number(id.slice(4))}/${encodeURIComponent(f.name)}`;
	await download(url, path.join(dest, f.name));
	return f.name;
}

async function modrinth(slug) {
	const v = await json(`https://api.modrinth.com/v2/project/${slug}/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.16.5%22%5D`);
	if (!v.length) throw new Error("no 1.16.5 version");
	const file = v[0].files.find((f) => f.primary) || v[0].files[0];
	await download(file.url, path.join(dest, file.filename));
	return file.filename;
}

(async () => {
	for (const s of slugs) {
		try {
			const name = s.startsWith("mr:") ? await modrinth(s.slice(3)) : await curseforge(s);
			console.log("OK   " + s + " -> " + name);
		} catch (e) {
			console.log("FAIL " + s + ": " + e.message);
		}
	}
})();
