package dev.gtav.trainer;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable command catalog loaded from assets/command_catalog.json plus any item assets it references.
 * The same files are the allow-list for tools/validate_commands.py, so UI and validator cannot drift.
 */
final class CommandCatalog {
    static final String ASSET_NAME = "command_catalog.json";
    static final int SUPPORTED_SCHEMA = 2;
    static final int MAX_RESULTS_CEILING = 200;

    /** Reads a named asset as UTF-8; abstracted so parsing does not depend on Context. */
    interface AssetSource {
        String read(String name) throws IOException;
    }

    /** One selectable entry in an item asset (weapon, vehicle, ...). */
    static final class Item {
        final String id;
        final String name;
        final String group;
        final String availability;
        private final String searchKey;
        Item(String id, String name, String group, String availability) {
            this.id = id; this.name = name; this.group = group; this.availability = availability;
            this.searchKey = (id + " " + name + " " + group).toLowerCase(Locale.ROOT);
        }
    }

    static final class SearchResult {
        final List<Item> shown;
        final int totalMatches;
        SearchResult(List<Item> shown, int totalMatches) { this.shown = shown; this.totalMatches = totalMatches; }
    }

    /** Item list attached to an action via its "items" parameter block. */
    static final class ItemSet {
        final String asset;
        final int maxResults;
        final List<Item> items;
        private final Map<String, Item> byId;
        ItemSet(String asset, int maxResults, List<Item> items, Map<String, Item> byId) {
            this.asset = asset; this.maxResults = maxResults;
            this.items = Collections.unmodifiableList(items); this.byId = byId;
        }
        Item find(String id) { return byId.get(id); }

        /** Case-insensitive substring match on id, name and group; capped at maxResults. */
        SearchResult search(String query) {
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            List<Item> shown = new ArrayList<>();
            int total = 0;
            for (Item item : items) {
                if (!q.isEmpty() && !item.searchKey.contains(q)) continue;
                total++;
                if (shown.size() < maxResults) shown.add(item);
            }
            return new SearchResult(Collections.unmodifiableList(shown), total);
        }
    }

    static final class Action {
        final String id;
        final String label;
        final String categoryId;
        /** Null for plain actions; non-null when the action requires a selected item. */
        final ItemSet items;
        Action(String id, String label, String categoryId, ItemSet items) {
            this.id = id; this.label = label; this.categoryId = categoryId; this.items = items;
        }
    }

    static final class Category {
        final String id;
        final String label;
        final List<Action> actions;
        Category(String id, String label, List<Action> actions) {
            this.id = id; this.label = label; this.actions = Collections.unmodifiableList(actions);
        }
    }

    final int schemaVersion;
    final String targetPackage;
    final List<Category> categories;
    private final Map<String, Action> actionsById;

    private CommandCatalog(int schemaVersion, String targetPackage, List<Category> categories,
                           Map<String, Action> actionsById) {
        this.schemaVersion = schemaVersion;
        this.targetPackage = targetPackage;
        this.categories = Collections.unmodifiableList(categories);
        this.actionsById = actionsById;
    }

    static CommandCatalog load(Context context) throws IOException, JSONException {
        AssetSource assets = name -> {
            try (InputStream in = context.getAssets().open(name)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        };
        return parse(assets.read(ASSET_NAME), assets);
    }

    /** Parses and validates; throws IllegalArgumentException on semantic errors. */
    static CommandCatalog parse(String json, AssetSource assets) throws JSONException, IOException {
        JSONObject root = new JSONObject(json);
        int schema = root.getInt("schema_version");
        if (schema != SUPPORTED_SCHEMA) {
            throw new IllegalArgumentException("Unsupported catalog schema_version " + schema);
        }
        String target = requireText(root, "target_package");
        JSONArray rawCategories = root.getJSONArray("categories");
        List<Category> categories = new ArrayList<>();
        Map<String, Action> byId = new HashMap<>();
        Map<String, ItemSet> loadedAssets = new HashMap<>();
        for (int c = 0; c < rawCategories.length(); c++) {
            JSONObject rawCategory = rawCategories.getJSONObject(c);
            String categoryId = requireText(rawCategory, "id");
            String categoryLabel = requireText(rawCategory, "label");
            JSONArray rawActions = rawCategory.getJSONArray("actions");
            List<Action> actions = new ArrayList<>();
            for (int a = 0; a < rawActions.length(); a++) {
                JSONObject rawAction = rawActions.getJSONObject(a);
                String actionId = requireText(rawAction, "id");
                if (!actionId.startsWith(categoryId + ".")) {
                    throw new IllegalArgumentException("Action " + actionId + " not prefixed by " + categoryId + ".");
                }
                if (byId.containsKey(actionId)) {
                    throw new IllegalArgumentException("Duplicate action id " + actionId);
                }
                ItemSet items = null;
                JSONObject rawItems = rawAction.optJSONObject("items");
                if (rawItems != null) {
                    String asset = requireText(rawItems, "asset");
                    int maxResults = rawItems.optInt("max_results", 60);
                    if (maxResults < 1 || maxResults > MAX_RESULTS_CEILING) {
                        throw new IllegalArgumentException("max_results out of range for " + actionId);
                    }
                    ItemSet shared = loadedAssets.get(asset);
                    if (shared == null) {
                        shared = parseItems(asset, assets.read(asset), maxResults);
                        loadedAssets.put(asset, shared);
                    }
                    items = new ItemSet(asset, maxResults, new ArrayList<>(shared.items), shared.byId);
                }
                Action action = new Action(actionId, requireText(rawAction, "label"), categoryId, items);
                byId.put(actionId, action);
                actions.add(action);
            }
            if (actions.isEmpty()) throw new IllegalArgumentException("Category " + categoryId + " has no actions");
            categories.add(new Category(categoryId, categoryLabel, actions));
        }
        if (categories.isEmpty()) throw new IllegalArgumentException("Catalog has no categories");
        return new CommandCatalog(schema, target, categories, byId);
    }

    private static ItemSet parseItems(String asset, String json, int maxResults) throws JSONException {
        JSONArray raw = new JSONArray(json);
        List<Item> items = new ArrayList<>();
        Map<String, Item> byId = new HashMap<>();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject entry = raw.getJSONObject(i);
            String id = requireText(entry, "id");
            if (byId.containsKey(id)) throw new IllegalArgumentException("Duplicate item " + id + " in " + asset);
            Item item = new Item(id, requireText(entry, "name"),
                    entry.optString("category", "").trim(), entry.optString("availability", "UNKNOWN").trim());
            byId.put(id, item);
            items.add(item);
        }
        if (items.isEmpty()) throw new IllegalArgumentException(asset + " has no items");
        return new ItemSet(asset, maxResults, items, byId);
    }

    Action find(String actionId) {
        return actionsById.get(actionId);
    }

    private static String requireText(JSONObject object, String key) throws JSONException {
        String value = object.getString(key).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Empty value for " + key);
        return value;
    }
}
