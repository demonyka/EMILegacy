package dev.emi.emi.runtime;



import java.nio.charset.StandardCharsets;
import java.io.Writer;
import java.io.Reader;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import dev.emi.emi.EmiPort;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dev.emi.emi.bom.BoM;
import net.minecraft.util.JsonHelper;

public class EmiPersistentData {
	public static final File FILE = new File("emi.json");
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	
	public static void save() {
		try {
			JsonObject json = new JsonObject();
			json.add("favorites", EmiFavorites.save());
			EmiSidebars.save(json);
			json.add("recipe_defaults", BoM.saveAdded());
			json.add("hidden_stacks", EmiHidden.save());
			try (Writer writer = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
		} catch (Exception e) {
			EmiLog.error("Failed to write persistent data", e);
		}
	}

	public static void load() {
		if (!FILE.exists()) {
			return;
		}
		try {
			JsonObject json;
			try (Reader reader = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
				json = GSON.fromJson(reader, JsonObject.class);
			}
			if (json == null) {
				return;
			}
			if (JsonHelper.hasArray(json, "favorites")) {
				EmiFavorites.load(JsonHelper.getArray(json, "favorites"));
			}
			EmiSidebars.load(json);
			if (EmiPort.hasJsonObject(json, "recipe_defaults")) {
				BoM.loadAdded(JsonHelper.getObject(json, "recipe_defaults"));
			}
			if (JsonHelper.hasArray(json, "hidden_stacks")) {
				EmiHidden.load(JsonHelper.getArray(json, "hidden_stacks"));
			}
		} catch (Exception e) {
			EmiLog.error("Failed to parse persistent data", e);
		}
	}
}
