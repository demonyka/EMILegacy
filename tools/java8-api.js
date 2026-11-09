// Mechanical rewrite of Java 9-17 library calls to Java 8 equivalents.
// Syntax-only features (switch arrows, instanceof patterns, records) are handled by Jabel at compile time;
// records still need the @Desugar marker, which this script adds.
// Usage: node tools/java8-api.js <src dir>
const fs = require("fs");
const path = require("path");

const rules = [
	[/(?<![\w.])List\.of\(/g, "ImmutableList.of(", "com.google.common.collect.ImmutableList"],
	[/(?<![\w.])List\.copyOf\(/g, "ImmutableList.copyOf(", "com.google.common.collect.ImmutableList"],
	[/(?<![\w.])Set\.of\(/g, "ImmutableSet.of(", "com.google.common.collect.ImmutableSet"],
	[/(?<![\w.])Set\.copyOf\(/g, "ImmutableSet.copyOf(", "com.google.common.collect.ImmutableSet"],
	[/(?<![\w.])Map\.of\(/g, "ImmutableMap.of(", "com.google.common.collect.ImmutableMap"],
	[/(?<![\w.])Map\.copyOf\(/g, "ImmutableMap.copyOf(", "com.google.common.collect.ImmutableMap"],
	[/(?<![\w.])Map\.entry\(/g, "Maps.immutableEntry(", "com.google.common.collect.Maps"],
	[/(?<![\w.]Collectors)\.toList\(\)/g,".collect(Collectors.toList())", "java.util.stream.Collectors"],
	[/\.isBlank\(\)/g, ".trim().isEmpty()", null],
	[/\.strip\(\)/g, ".trim()", null],
	[/^(\s*)((?:public |private |protected |static |final )*)record (\w+)/gm, "$1@Desugar\n$1$2record $3", "com.github.bsideup.jabel.Desugar"],
];

let changedFiles = 0;
function walk(dir) {
	for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
		const p = path.join(dir, e.name);
		if (e.isDirectory()) walk(p);
		else if (p.endsWith(".java")) fix(p);
	}
}

function fix(p) {
	const orig = fs.readFileSync(p, "utf8");
	let text = orig;
	const imports = new Set();
	for (const [re, rep, imp] of rules) {
		const next = text.replace(re, rep);
		if (next !== text && imp) imports.add(imp);
		text = next;
	}
	for (const imp of imports) {
		if (new RegExp("^import " + imp.replace(/\./g, "\\.") + ";", "m").test(text)) continue;
		text = text.replace(/^(package [\w.]+;\s*\n)/m, `$1\nimport ${imp};\n`);
	}
	if (text !== orig) {
		fs.writeFileSync(p, text);
		changedFiles++;
	}
}

walk(process.argv[2]);
console.log("files changed:", changedFiles);
