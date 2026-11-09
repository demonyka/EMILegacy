// Rewrites Minecraft 1.17+ idioms in EMI sources to 1.16.5 equivalents / dev.emi.emi.backport classes.
// Usage: node tools/backport-idioms.js <src dir>
const fs = require("fs");
const path = require("path");

const GL11 = "org.lwjgl.opengl.GL11";
const rules = [
	// [regex, replacement, import to add]
	[/^import net\.minecraft\.client\.gui\.tooltip\.TooltipComponent;/gm, "import dev.emi.emi.backport.TooltipComponent;"],
	[/^import net\.minecraft\.client\.gui\.tooltip\.OrderedTextTooltipComponent;/gm, "import dev.emi.emi.backport.OrderedTextTooltipComponent;"],
	[/^import net\.minecraft\.tag\.TagKey;/gm, "import dev.emi.emi.backport.TagKey;"],
	[/^import net\.minecraft\.client\.render\.VertexFormat\.DrawMode;\n/gm, ""],
	[/^import dev\.emi\.emi\.mixin\.accessor\.OrderedTextTooltipComponentAccessor;\n/gm, ""],
	[/\(\(OrderedTextTooltipComponentAccessor\) ?([\w.()]+)\)\.getText\(\)/g, "((OrderedTextTooltipComponent) $1).getText()", "dev.emi.emi.backport.OrderedTextTooltipComponent"],
	// model-view stack -> GL matrix stack
	[/MatrixStack (\w+) = RenderSystem\.getModelViewStack\(\);/g, "ModelViewStack $1 = ModelViewStack.INSTANCE;", "dev.emi.emi.backport.ModelViewStack"],
	[/^\s*(RenderSystem|EmiPort)\.applyModelViewMatrix\(\);\n/gm, ""],
	// shaders -> fixed function
	[/RenderSystem\.setShaderTexture\(0, /g, "EmiPort.bindTexture(", "dev.emi.emi.EmiPort"],
	[/RenderSystem\.setShaderColor\(/g, "RenderSystem.color4f("],
	[/^\s*RenderSystem\.setShader\(GameRenderer::\w+\);\n/gm, ""],
	[/^\s*EmiPort\.setPosition(Color)?TexShader\(\);\n/gm, ""],
	[/(VertexFormat\.)?DrawMode\.QUADS/g, "GL11.GL_QUADS", GL11],
	[/(VertexFormat\.)?DrawMode\.TRIANGLES/g, "GL11.GL_TRIANGLES", GL11],
	[/(VertexFormat\.)?DrawMode\.LINES/g, "GL11.GL_LINES", GL11],
];

let changedFiles = 0;
function walk(dir) {
	for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
		const p = path.join(dir, e.name);
		if (e.isDirectory()) walk(p);
		else if (p.endsWith(".java") && !p.includes(path.sep + "backport" + path.sep)) fix(p);
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
	const pkg = (text.match(/^package ([\w.]+);/m) || [])[1];
	for (const imp of imports) {
		if (imp.slice(0, imp.lastIndexOf(".")) === pkg) continue;
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
