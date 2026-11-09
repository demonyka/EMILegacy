package dev.emi.emi.jemi;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiPatternCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiPluginContainer;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.runtime.EmiReloadManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaRecipeCategoryUid;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.ISubtypeManager;
import mezz.jei.api.ingredients.subtypes.UidContext;
import dev.emi.emi.api.EmiApi;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.input.IClickedIngredient;
import mezz.jei.plugins.jei.info.IngredientInfoRecipe;
import mezz.jei.recipes.RecipeTransferManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fluids.FluidStack;

/**
 * JEMI: EMI's bridge to JEI 7. JEI instantiates this class as a JEI plugin (to receive the runtime),
 * EMI instantiates it as an EMI plugin (see EmiReloadManager) to convert every JEI category,
 * catalyst and recipe of mods without a native EMI plugin.
 */
@JeiPlugin
public class JemiPlugin implements IModPlugin, EmiPlugin {
	// Filled by the reload thread, read by the render thread
	private static final Map<EmiRecipeCategory, IRecipeCategory<?>> CATEGORY_MAP = new ConcurrentHashMap<>();
	private static final Map<Identifier, EmiRecipeCategory> CATEGORIES_BY_UID = new ConcurrentHashMap<>();
	private static final Map<List<Object>, JemiRecipeHandler<?, ?>> HANDLERS = new ConcurrentHashMap<>();
	private static ISubtypeManager subtypeManager;
	public static volatile IJeiRuntime runtime;

	static {
		// JEI 7 has no "runtime unavailable" callback, drop the runtime when leaving the world
		try {
			MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggedOutEvent.class, event -> runtime = null);
		} catch (Throwable t) {
			EmiLog.error("[JEMI] Could not register logout listener", t);
		}
	}

	@Override
	public Identifier getPluginUid() {
		return EmiPort.id("emi:jemi");
	}

	@Override
	public void registerItemSubtypes(ISubtypeRegistration registration) {
		// First callback of every JEI (re)load: the previous runtime is stale from here on
		runtime = null;
	}

	@Override
	public void registerIngredients(IModIngredientRegistration registration) {
		subtypeManager = registration.getSubtypeManager();
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		JemiPlugin.runtime = runtime;
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void register(EmiRegistry registry) {
		EmiLog.info("[JEMI] Waiting for JEI to finish reloading...");
		EmiReloadManager.step(EmiPort.literal("Waiting for JEI to finish..."), 20_000);
		try {
			while (runtime == null) {
				Thread.sleep(100);
			}
			// The runtime is handed out while JEI is still starting on the main thread (later plugins,
			// sorting): continue only after the main thread has left JEI's startup
			MinecraftClient.getInstance().submit(() -> {}).get(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			return;
		} catch (ExecutionException | TimeoutException e) {
			EmiLog.warn("[JEMI] Could not wait for the main thread, reading JEI anyway");
		}
		IJeiRuntime runtime = JemiPlugin.runtime;
		if (runtime == null) {
			EmiLog.warn("[JEMI] JEI restarted while EMI was waiting for it, skipping");
			return;
		}
		EmiLog.info("[JEMI] JEI reloaded!");
		Set<String> handledNamespaces = EmiAgnos.getPlugins().stream().map(EmiPluginContainer::id).collect(Collectors.toSet());

		EmiReloadManager.step(EmiPort.literal("Loading information from JEI..."), 5_000);
		registry.addGenericExclusionArea((screen, consumer) -> {
			GuiScreenHelper helper = JemiInternals.getScreenHelper();
			if (helper != null && screen == MinecraftClient.getInstance().currentScreen) {
				helper.updateGuiExclusionAreas();
				for (Rect2i r : helper.getGuiExclusionAreas()) {
					if (r != null) {
						consumer.accept(new Bounds(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
					}
				}
			}
		});

		registry.addGenericStackProvider((screen, x, y) -> {
			GuiScreenHelper helper = JemiInternals.getScreenHelper();
			if (helper != null && screen instanceof HandledScreen<?> hs) {
				try {
					IClickedIngredient<?> clicked = helper.getPluginsIngredientUnderMouse(hs, x, y);
					if (clicked != null) {
						EmiStack stack = JemiUtil.getStack(clicked.getValue());
						if (!stack.isEmpty()) {
							return new EmiStackInteraction(stack, null, false);
						}
					}
				} catch (Throwable t) {
				}
			}
			return EmiStackInteraction.EMPTY;
		});

		registry.addGenericDragDropHandler(new JemiDragDropHandler());

		registry.addIngredientSerializer(JemiStack.class, new JemiStackSerializer(runtime.getIngredientManager()));

		EmiReloadManager.step(EmiPort.literal("Processing JEI stacks..."), 5_000);
		IIngredientManager im = runtime.getIngredientManager();
		for (IIngredientType<?> type : im.getRegisteredIngredientTypes()) {
			if (type == VanillaTypes.ITEM || type == VanillaTypes.FLUID) {
				continue;
			}
			for (Object o : im.getAllIngredients(type)) {
				EmiStack stack = JemiUtil.getStack(type, o);
				if (!stack.isEmpty()) {
					registry.addEmiStack(stack);
				}
			}
		}
		// JEI 7 has no public ingredient visibility query, hidden JEI ingredients are not mirrored

		EmiReloadManager.step(EmiPort.literal("Processing JEI subtypes..."), 5_000);
		safely("subtype comparison", () -> parseSubtypes(registry));

		EmiReloadManager.step(EmiPort.literal("Processing JEI recipes..."), 5_000);
		Set<Identifier> existingCategories = EmiRecipes.categories.stream().map(EmiRecipeCategory::getId).collect(Collectors.toSet());
		Map<Identifier, EmiRecipeCategory> categoryMap = Maps.newHashMap();
		categoryMap.put(VanillaRecipeCategoryUid.CRAFTING, VanillaEmiRecipeCategories.CRAFTING);
		categoryMap.put(VanillaRecipeCategoryUid.FURNACE, VanillaEmiRecipeCategories.SMELTING);
		categoryMap.put(VanillaRecipeCategoryUid.BLASTING, VanillaEmiRecipeCategories.BLASTING);
		categoryMap.put(VanillaRecipeCategoryUid.SMOKING, VanillaEmiRecipeCategories.SMOKING);
		categoryMap.put(VanillaRecipeCategoryUid.CAMPFIRE, VanillaEmiRecipeCategories.CAMPFIRE_COOKING);
		categoryMap.put(VanillaRecipeCategoryUid.STONECUTTING, VanillaEmiRecipeCategories.STONECUTTING);
		categoryMap.put(VanillaRecipeCategoryUid.SMITHING, VanillaEmiRecipeCategories.SMITHING);
		categoryMap.put(VanillaRecipeCategoryUid.ANVIL, VanillaEmiRecipeCategories.ANVIL_REPAIRING);
		categoryMap.put(VanillaRecipeCategoryUid.BREWING, VanillaEmiRecipeCategories.BREWING);
		categoryMap.put(VanillaRecipeCategoryUid.FUEL, VanillaEmiRecipeCategories.FUEL);
		categoryMap.put(VanillaRecipeCategoryUid.INFORMATION, VanillaEmiRecipeCategories.INFO);

		CATEGORY_MAP.clear();
		CATEGORIES_BY_UID.clear();
		HANDLERS.clear();
		EmiRecipeFiller.extraHandlers = JemiPlugin::getRecipeHandler;

		IRecipeManager rm = runtime.getRecipeManager();
		List<IRecipeCategory<?>> categories = rm.getRecipeCategories((IFocus<?>) null, true);
		for (IRecipeCategory<?> c : categories) {
			Identifier id = c.getUid();
			String title = id.toString();
			try {
				title = c.getTitleAsTextComponent().getString();
			} catch (Throwable t) {
			}
			EmiLog.info("[JEMI] Collecting data for " + title);
			EmiReloadManager.step(EmiPort.literal("Loading JEI data for " + title), 5_000);
			try {
				List<EmiStack> catalysts = rm.getRecipeCatalysts(c, true).stream().map(JemiUtil::getStack).collect(Collectors.toList());
				if (categoryMap.containsKey(id)) {
					EmiRecipeCategory category = categoryMap.get(id);
					CATEGORY_MAP.put(category, c);
					CATEGORIES_BY_UID.put(id, category);
					for (EmiStack catalyst : catalysts) {
						if (!catalyst.isEmpty()) {
							registry.addWorkstation(category, catalyst);
						}
					}
					if (id.equals(VanillaRecipeCategoryUid.INFORMATION)) {
						addInfoRecipes(registry, getRecipes(rm, c));
					} else if (id.equals(VanillaRecipeCategoryUid.CRAFTING)) {
						addCraftingRecipes(registry, (IRecipeCategory) c, (List) getRecipes(rm, c));
					}
					continue;
				}
				if (handledNamespaces.contains(id.getNamespace())) {
					EmiLog.info("[JEMI] Skipping recipe category " + id + " because mod is already handled");
					continue;
				}
				if (existingCategories.contains(id)) {
					EmiLog.info("[JEMI] Skipping recipe category " + id + " because native EMI recipe category already exists");
					continue;
				}
				EmiRecipeCategory category = new JemiCategory(c);
				CATEGORY_MAP.put(category, c);
				CATEGORIES_BY_UID.put(id, category);
				registry.addCategory(category);
				for (EmiStack catalyst : catalysts) {
					if (!catalyst.isEmpty()) {
						registry.addWorkstation(category, catalyst);
					}
				}
				for (Object r : getRecipes(rm, c)) {
					try {
						registry.addRecipe(new JemiRecipe(category, c, r));
					} catch (Throwable t) {
						EmiLog.error("[JEMI] Exception thrown adding JEI recipe", t);
					}
				}
			} catch (Throwable t) {
				EmiLog.error("[JEMI] Exception thrown adding JEI recipes", t);
			}
		}
	}

	private static <T> List<T> getRecipes(IRecipeManager rm, IRecipeCategory<T> category) {
		return rm.getRecipes(category, (IFocus<?>) null, true);
	}

	private void addInfoRecipes(EmiRegistry registry, List<?> recipes) {
		Map<List<EmiStack>, List<IngredientInfoRecipe<?>>> grouped = Maps.newLinkedHashMap();
		for (Object o : recipes) {
			if (o instanceof IngredientInfoRecipe<?> recipe) {
				List<EmiStack> stacks = recipe.getIngredients().stream().map(i -> JemiUtil.getStack(recipe.getIngredientType(), i))
					.filter(s -> !s.isEmpty()).collect(Collectors.toList());
				grouped.computeIfAbsent(stacks, k -> Lists.newArrayList()).add(recipe);
			}
		}
		Map<String, List<EmiStack>> identical = Maps.newLinkedHashMap();
		Map<String, Text> texts = Maps.newHashMap();
		for (Map.Entry<List<EmiStack>, List<IngredientInfoRecipe<?>>> group : grouped.entrySet()) {
			MutableText text = EmiPort.literal("");
			for (IngredientInfoRecipe<?> recipe : group.getValue()) {
				for (StringVisitable sv : recipe.getDescription()) {
					MutableText current = EmiPort.literal("");
					sv.visit((style, string) -> {
						current.append(EmiPort.literal(string, style));
						return Optional.empty();
					}, Style.EMPTY);
					if (!current.getString().trim().isEmpty()) {
						if (!text.getString().isEmpty()) {
							text.append(" ");
						}
						text.append(current);
					}
				}
			}
			String key = text.getString();
			texts.putIfAbsent(key, text);
			identical.computeIfAbsent(key, k -> Lists.newArrayList()).addAll(group.getKey());
		}

		for (Map.Entry<String, List<EmiStack>> entry : identical.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			registry.addRecipe(new EmiInfoRecipe(entry.getValue().stream().map(s -> (EmiIngredient) s).collect(Collectors.toList()),
				ImmutableList.of(texts.get(entry.getKey())), null));
		}
	}

	/**
	 * EMI's vanilla plugin already shows every non-special crafting recipe from the recipe manager.
	 * JEI 7 crafting recipes that EMI cannot know about are added here: recipes registered only to JEI
	 * (display recipes for special crafting, generated recipes) and special recipes from mods.
	 */
	private void addCraftingRecipes(EmiRegistry registry, IRecipeCategory<CraftingRecipe> category, List<CraftingRecipe> recipes) {
		for (CraftingRecipe recipe : recipes) {
			try {
				Identifier id = recipe.getId();
				// JEI generates tipped arrow and suspicious stew recipes that EMI already provides natively
				if (id != null && id.getNamespace().equals("minecraft")
						&& (id.getPath().startsWith("jei.tipped.arrow") || id.getPath().startsWith("jei.suspicious.stew"))) {
					continue;
				}
				boolean known = id != null && registry.getRecipeManager().get(id).isPresent();
				boolean modSpecial = recipe instanceof SpecialCraftingRecipe && id != null && !"minecraft".equals(id.getNamespace());
				if (known && !modSpecial) {
					continue;
				}
				if (!category.isHandled(recipe)) {
					continue;
				}
				JemiIngredients ingredients = new JemiIngredients();
				category.setIngredients(recipe, ingredients);
				List<List<ItemStack>> rawInputs = ingredients.getInputs(VanillaTypes.ITEM);
				List<List<ItemStack>> rawOutputs = ingredients.getOutputs(VanillaTypes.ITEM);
				if (rawInputs.size() > 9 || rawOutputs.isEmpty()) {
					continue;
				}
				boolean shapeless = !(recipe instanceof IShapedRecipe<?>);
				List<EmiIngredient> inputs = Lists.newArrayList();
				if (shapeless) {
					for (List<ItemStack> slot : rawInputs) {
						inputs.add(JemiUtil.getIngredient(VanillaTypes.ITEM, slot));
					}
				} else {
					IShapedRecipe<?> shaped = (IShapedRecipe<?>) recipe;
					int w = shaped.getRecipeWidth();
					int h = shaped.getRecipeHeight();
					if (w > 3 || h > 3 || w * h != rawInputs.size()) {
						continue;
					}
					for (int i = 0; i < 9; i++) {
						int x = i % 3, y = i / 3;
						if (x < w && y < h) {
							inputs.add(JemiUtil.getIngredient(VanillaTypes.ITEM, rawInputs.get(y * w + x)));
						} else {
							inputs.add(EmiStack.EMPTY);
						}
					}
				}
				List<EmiStack> outputs = JemiUtil.getIngredient(VanillaTypes.ITEM, rawOutputs.get(0)).getEmiStacks()
					.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
				if (inputs.stream().allMatch(EmiIngredient::isEmpty) || outputs.isEmpty()) {
					continue;
				}
				if (id != null && !known) {
					// Generated by a JEI plugin, not in the recipe manager: mark the id as synthetic
					id = EmiPort.id("jei", "/crafting/" + EmiUtil.subId(id));
				}
				EmiRecipe emiRecipe;
				if (outputs.size() > 1) {
					emiRecipe = new EmiPatternCraftingRecipe(inputs, EmiStack.EMPTY, id, shapeless) {

						@Override
						public List<EmiStack> getOutputs() {
							return outputs;
						}

						@Override
						public SlotWidget getInputWidget(int slot, int x, int y) {
							if (slot < inputs.size()) {
								return new SlotWidget(inputs.get(slot), x, y);
							} else {
								return new SlotWidget(EmiStack.EMPTY, x, y);
							}
						}

						@Override
						public SlotWidget getOutputWidget(int x, int y) {
							return new GeneratedSlotWidget(r -> outputs.get(r.nextInt(outputs.size())), recipe.hashCode(), x, y);
						}
					};
				} else {
					emiRecipe = new EmiCraftingRecipe(inputs, outputs.get(0), id, shapeless);
				}
				registry.addRecipe(emiRecipe);
			} catch (Throwable t) {
				EmiLog.error("[JEMI] Exception thrown adding JEI crafting recipe", t);
			}
		}
	}

	private void parseSubtypes(EmiRegistry registry) {
		if (subtypeManager == null) {
			return;
		}
		ISubtypeManager sm = subtypeManager;
		for (Item item : EmiPort.getItemRegistry()) {
			try {
				ItemStack stack = item.getDefaultStack();
				if (!stack.isEmpty() && sm.getSubtypeInfo(stack, UidContext.Recipe) != null) {
					registry.setDefaultComparison(item, Comparison.compareData(s -> {
						ItemStack is = s.getItemStack();
						return is.isEmpty() ? null : sm.getSubtypeInfo(is, UidContext.Recipe);
					}));
				}
			} catch (Throwable t) {
				EmiReloadLog.warn("Exception adding default comparison for JEI item subtype", t);
			}
		}
		for (Fluid fluid : EmiPort.getFluidRegistry()) {
			try {
				if (fluid == Fluids.EMPTY) {
					continue;
				}
				if (sm.getSubtypeInfo(new FluidStack(fluid, 1000), UidContext.Recipe) != null) {
					registry.setDefaultComparison(fluid, Comparison.compareData(s -> {
						return sm.getSubtypeInfo(new FluidStack(fluid, 1000, s.getNbt()), UidContext.Recipe);
					}));
				}
			} catch (Throwable t) {
				EmiReloadLog.warn("Exception adding default comparison for JEI fluid subtype", t);
			}
		}
		// JEI 7 subtype interpreters only exist for items and fluids
	}

	/**
	 * JEI wants to open its recipe screen for an ingredient (its own key binds, other mods through the JEI API).
	 * @return true if EMI took over
	 */
	public static boolean showFocus(IFocus<?> focus) {
		try {
			if (focus == null || runtime == null || !EmiReloadManager.isLoaded()) {
				return false;
			}
			EmiStack stack = JemiUtil.getStack(focus.getValue());
			if (stack.isEmpty()) {
				return false;
			}
			if (focus.getMode() == IFocus.Mode.INPUT) {
				EmiApi.displayUses(stack);
			} else {
				EmiApi.displayRecipes(stack);
			}
			return true;
		} catch (Throwable t) {
			EmiLog.error("[JEMI] Could not show recipes requested through JEI", t);
			return false;
		}
	}

	/**
	 * JEI wants to open its recipe screen for categories (clickable areas of machine screens such as progress arrows).
	 * @return true if EMI took over
	 */
	public static boolean showCategories(List<Identifier> uids) {
		try {
			if (uids == null || runtime == null || !EmiReloadManager.isLoaded()) {
				return false;
			}
			for (Identifier uid : uids) {
				EmiRecipeCategory category = CATEGORIES_BY_UID.get(uid);
				if (category != null && !EmiApi.getRecipeManager().getRecipes(category).isEmpty()) {
					EmiApi.displayRecipeCategory(category);
					return true;
				}
			}
		} catch (Throwable t) {
			EmiLog.error("[JEMI] Could not show recipe categories requested through JEI", t);
		}
		return false;
	}

	private static EmiRecipeHandler<?> getRecipeHandler(ScreenHandler handler, EmiRecipe recipe) {
		IRecipeCategory<?> category = CATEGORY_MAP.get(recipe.getCategory());
		if (category == null) {
			return null;
		}
		RecipeTransferManager manager = JemiInternals.getTransferManager();
		if (manager == null) {
			return null;
		}
		try {
			IRecipeTransferHandler<?> transfer = manager.getRecipeTransferHandler(handler, category);
			if (transfer == null) {
				return null;
			}
			return HANDLERS.computeIfAbsent(Arrays.asList(transfer, category), k -> createHandler(transfer, category));
		} catch (Throwable t) {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static JemiRecipeHandler<?, ?> createHandler(IRecipeTransferHandler<?> transfer, IRecipeCategory<?> category) {
		return new JemiRecipeHandler((IRecipeTransferHandler) transfer, (IRecipeCategory) category);
	}

	private static void safely(String name, Runnable runnable) {
		try {
			runnable.run();
		} catch (Throwable t) {
			EmiReloadLog.warn("Exception thrown when reloading " + name  + " step in JEMI plugin", t);
		}
	}
}
