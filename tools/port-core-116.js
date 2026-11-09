// Targeted 1.19.2 -> 1.16.5 API rewrites for EMI core sources (not jemi/platform/network).
// Each rule: [file glob-ish substring or null for all, regex, replacement, import].
// Usage: node tools/port-core-116.js <src dir>
const fs = require("fs");
const path = require("path");

const PORT = "dev.emi.emi.EmiPort";
const rules = [
	// cursor stack is in PlayerInventory on 1.16
	[null, /\b[\w.()]+\.getScreenHandler\(\)\.getCursorStack\(\)/g, "EmiPort.getCursorStack()", PORT],
	[null, /\b[\w.()]+\.getScreenHandler\(\)\.setCursorStack\(/g, "EmiPort.setCursorStack(", PORT],
	[null, /client\.player\.currentScreenHandler\.setCursorStack\(/g, "EmiPort.setCursorStack(", PORT],
	[null, /screenHandler\.getCursorStack\(\)/g, "EmiPort.getCursorStack()", PORT],
	[null, /\b(entity|player|client\.player)\.getInventory\(\)/g, "$1.inventory"],
	[null, /\.getAbilities\(\)/g, ".abilities"],
	[null, /client\.player\.sendCommand\((\w+)\)/g, 'client.player.sendChatMessage("/" + $1)'],
	[null, /ItemStack\.canCombine\(/g, "EmiPort.canCombine(", PORT],
	[null, /slot\.getIndex\(\)/g, "slot.getSlotIndex()"],
	[null, /\.isOf\((Items\.\w+)\)/g, ".getItem() == $1"],
	[null, /NbtElement\.COMPOUND_TYPE/g, "10"],
	[null, /BlockItem\.setBlockEntityNbt\((\w+), BlockEntityType\.BANNER, (\w+)\)/g, '$1.putSubTag("BlockEntityTag", $2)'],
	[null, /WrittenBookItem\.TITLE_KEY/g, '"title"'],
	[null, /WrittenBookItem\.AUTHOR_KEY/g, '"author"'],
	[null, /WrittenBookItem\.GENERATION_KEY/g, '"generation"'],
	[null, /LightmapTextureManager\.MAX_LIGHT_COORDINATE/g, "0xF000F0"],
	[null, /\.getHeldItemModel\(([^;]*?), null, null, 0\)/g, ".getHeldItemModel($1, null, null)"],
	[null, /Style\.EMPTY\.withColor\((0x[0-9a-fA-F]+|\w+)\)/g, "EmiPort.color($1)", PORT],
	[null, /String::isBlank/g, "s -> s.trim().isEmpty()"],
	[null, /IntList\.of\(/g, "EmiPort.intList(", PORT],
	[null, /new Gson\(\)\.newBuilder\(\)/g, "new GsonBuilder()", "com.google.gson.GsonBuilder"],
	[null, /JsonHelper\.hasJsonObject\(/g, "EmiPort.hasJsonObject(", PORT],
	[null, /JsonHelper\.hasNumber\(/g, "EmiPort.hasNumber(", PORT],
	[/[\\/](BoM|EmiData|EmiTagExclusionsLoader|RecipeDefaultLoader)\.java$/, /for \(String (\w+) : (\w+)\.keySet\(\)\)/g, "for (String $1 : EmiPort.keys($2))", PORT],
	[/[\\/]BoM\.java$/, /if \(!arr\.isEmpty\(\)\)/g, "if (arr.size() != 0)"],
	[/[\\/]IntGroup\.java$/, /values\.intStream\(\)\.mapToObj\(i -> "" \+ i\)/g, 'values.stream().map(i -> "" + i)'],
	[/[\\/]EmiTags\.java$/, /new ModelIdentifier\(id\.getNamespace\(\), ([^,]+), "inventory"\)/g, 'new ModelIdentifier(new Identifier(id.getNamespace(), $1), "inventory")', "net.minecraft.util.Identifier"],
	[/[\\/]EmiDrawContext\.java$/, /DrawableHelper\.drawCenteredTextWithShadow\(matrices, client\.textRenderer, text\.asOrderedText\(\), x, y, color\)/g, "DrawableHelper.drawCenteredText(matrices, client.textRenderer, text, x, y, color)"],
	[/[\\/]MouseMixin\.java$/, /client\.options\.getDiscreteMouseScroll\(\)\.getValue\(\)/g, "client.options.discreteMouseScroll"],
	[/[\\/]MouseMixin\.java$/, /client\.options\.getMouseWheelSensitivity\(\)\.getValue\(\)/g, "client.options.mouseWheelSensitivity"],
	[/[\\/]AbstractInventoryScreenMixin\.java$/, /this\.renderTooltip\(context\.raw\(\), list, Optional\.empty\(\), /g, "this.renderTooltip(context.raw(), list, "],
];

const skip = /[\\/](jemi|platform|network|forge|backport)[\\/]/;
let changed = 0;
function walk(dir) {
	for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
		const p = path.join(dir, e.name);
		if (e.isDirectory()) walk(p);
		else if (p.endsWith(".java") && !skip.test(p)) fix(p);
	}
}

function fix(p) {
	const orig = fs.readFileSync(p, "utf8");
	let text = orig;
	const imports = new Set();
	for (const [fileRe, re, rep, imp] of rules) {
		if (fileRe && !fileRe.test(p)) continue;
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
		changed++;
	}
}

walk(process.argv[2]);
console.log("files changed:", changed);
