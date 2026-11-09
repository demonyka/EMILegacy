// Launches the production Forge 1.16.5 client with real rendering, reusing the command line that
// HeadlessMC builds (captured once into $HMC/cmdline-template.txt) but with the original LWJGL jars
// instead of HeadlessMC's headless replacements. Usage: node tools/launch-prod.js <script.emis>
const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const hmc = process.env.HMC;
const script = process.argv[2];
const lib = path.join(hmc, "mc", "libraries");
// Windows command line: split on spaces outside double quotes
const raw = fs.readFileSync(path.join(hmc, "cmdline-template.txt"), "utf8").trim();
const args = [];
for (const m of raw.matchAll(/"([^"]*)"|(\S+)/g)) {
	args.push(m[1] !== undefined ? m[1] : m[2]);
}
const exe = args.shift();

// HeadlessMC deletes its per-launch folder; use the copy in $HMC/hmcrun instead
const runDir = path.join(hmc, "hmcrun");
const fixed = args.map((a) => a.replace(/[A-Za-z]:[^;= ]*[\\/]HeadlessMC[\\/][0-9a-f-]{36}/g, runDir)).map((a, i, all) => {
	if (a.startsWith("-Demi.devScript=")) return "-Demi.devScript=" + script;
	if (a.startsWith("-Djava.library.path=")) return "-Djava.library.path=" + path.join(hmc, "natives");
	if (all[i - 1] === "-cp") {
		return a.split(";").filter((e) => !e.endsWith("headlessmc-lwjgl.jar")).map((e) => {
			const m = e.match(/hmcrun[\\/](lwjgl(-[a-z]+)?)-3\.2\.2\.jar$/);
			return m ? path.join(lib, "org", "lwjgl", m[1], "3.2.2", m[1] + "-3.2.2.jar") : e;
		}).join(";");
	}
	return a;
});
// Extra JVM flags (e.g. -Dmixin.debug.verbose=true) via EXTRA_JVM
const extra = (process.env.EXTRA_JVM || "").split(" ").filter(Boolean);
const r = spawnSync(exe, extra.concat(fixed), { cwd: path.join(hmc, "game"), stdio: ["ignore", fs.openSync(path.join(hmc, "prod-stdout.txt"), "w"), fs.openSync(path.join(hmc, "prod-stdout.txt"), "a")], timeout: 900000 });
console.log("exit=" + r.status);
