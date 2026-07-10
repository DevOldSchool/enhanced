package com.inspect.item;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ItemInspectCache
{
	private static final String INDEX_FILE = "index.json";

	private final Gson gson;
	private final Path cacheDirectory;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
	{
		Thread thread = new Thread(runnable, "inspect-item-cache");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<Integer, ItemInspectInfo> memoryCache = new HashMap<>();
	private final Map<Integer, String> cacheKeysByItem = new HashMap<>();

	ItemInspectCache(Gson gson, Path cacheDirectory)
	{
		this.gson = gson;
		this.cacheDirectory = cacheDirectory;
	}

	CompletableFuture<Void> startUp(boolean clearCache)
	{
		if (clearCache)
		{
			return clearAsync();
		}

		return CompletableFuture.completedFuture(null);
	}

	void shutDown()
	{
		memoryCache.clear();
		cacheKeysByItem.clear();
		executor.shutdownNow();
	}

	CompletableFuture<Optional<ItemInspectInfo>> get(int itemId, long nowEpochSecond, int ttlDays)
	{
		synchronized (this)
		{
			ItemInspectInfo cached = memoryCache.get(itemId);
			if (cached != null)
			{
				if (!cached.isExpired(nowEpochSecond, ttlDays))
				{
					return CompletableFuture.completedFuture(Optional.of(cached));
				}
				memoryCache.remove(itemId);
			}
		}

		return CompletableFuture.supplyAsync(() -> readFromDisk(itemId, nowEpochSecond, ttlDays), executor);
	}

	CompletableFuture<Void> put(ItemInspectInfo info)
	{
		synchronized (this)
		{
			memoryCache.put(info.getItemId(), info);
			cacheKeysByItem.put(info.getItemId(), info.cacheKey());
		}

		return CompletableFuture.runAsync(() -> writeToDisk(info), executor);
	}

	CompletableFuture<Void> clearAsync()
	{
		synchronized (this)
		{
			memoryCache.clear();
			cacheKeysByItem.clear();
		}

		return CompletableFuture.runAsync(() ->
		{
			if (!Files.isDirectory(cacheDirectory)) {
				return;
			}

			try (Stream<Path> files = Files.list(cacheDirectory)) {
				files.forEach(path ->
				{
					try {
						Files.deleteIfExists(path);
					} catch (IOException ex) {
						log.debug("Unable to delete Item Inspect cache file {}", path, ex);
					}
				});
			} catch (IOException ex) {
				log.debug("Unable to clear Item Inspect cache", ex);
			}
		}, executor);
	}

	private Optional<ItemInspectInfo> readFromDisk(int itemId, long nowEpochSecond, int ttlDays)
	{
		try
		{
			Files.createDirectories(cacheDirectory);
			loadIndexIfNeeded();

			String cacheKey;
			synchronized (this)
			{
				cacheKey = cacheKeysByItem.get(itemId);
			}
			if (cacheKey == null)
			{
				return Optional.empty();
			}

			Path cacheFile = cachePath(cacheKey);
			if (!Files.isRegularFile(cacheFile))
			{
				return Optional.empty();
			}

			try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8))
			{
				ItemInspectInfo info = gson.fromJson(reader, ItemInspectInfo.class);
				if (info == null || info.getItemId() != itemId || info.isExpired(nowEpochSecond, ttlDays))
				{
					Files.deleteIfExists(cacheFile);
					removeIndex(itemId);
					return Optional.empty();
				}

				synchronized (this)
				{
					memoryCache.put(itemId, info);
					cacheKeysByItem.put(itemId, cacheKey);
				}
				return Optional.of(info);
			}
			catch (RuntimeException | IOException ex)
			{
				log.debug("Ignoring corrupt Item Inspect cache file {}", cacheFile, ex);
				Files.deleteIfExists(cacheFile);
				removeIndex(itemId);
				return Optional.empty();
			}
		}
		catch (IOException ex)
		{
			log.debug("Unable to read Item Inspect cache", ex);
			return Optional.empty();
		}
	}

	private void writeToDisk(ItemInspectInfo info)
	{
		try
		{
			Files.createDirectories(cacheDirectory);
			Path cacheFile = cachePath(info.cacheKey());
			Path tempFile = cacheDirectory.resolve(info.cacheKey() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8))
			{
				gson.toJson(info, writer);
			}
			Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			writeIndex();
		}
		catch (IOException ex)
		{
			log.debug("Unable to write Item Inspect cache", ex);
		}
	}

	private void loadIndexIfNeeded() throws IOException
	{
		synchronized (this)
		{
			if (!cacheKeysByItem.isEmpty())
			{
				return;
			}
		}

		Path indexFile = cacheDirectory.resolve(INDEX_FILE);
		if (!Files.isRegularFile(indexFile))
		{
			return;
		}

		Map<Integer, String> loadedCacheKeys = new HashMap<>();
		try (Reader reader = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8))
		{
			JsonObject index = gson.fromJson(reader, JsonObject.class);
			if (index == null)
			{
				return;
			}

			for (Map.Entry<String, JsonElement> entry : index.entrySet())
			{
				int itemId = Integer.parseInt(entry.getKey());
				String cacheKey = entry.getValue().getAsString();
				if (itemId < 0 || !isValidCacheKey(cacheKey))
				{
					throw new IllegalArgumentException("Invalid Item Inspect cache index entry");
				}
				loadedCacheKeys.put(itemId, cacheKey);
			}

			synchronized (this)
			{
				if (cacheKeysByItem.isEmpty())
				{
					cacheKeysByItem.putAll(loadedCacheKeys);
				}
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("Ignoring corrupt Item Inspect cache index", ex);
			Files.deleteIfExists(indexFile);
		}
	}

	private void writeIndex() throws IOException
	{
		JsonObject index = new JsonObject();
		synchronized (this)
		{
			for (Map.Entry<Integer, String> entry : cacheKeysByItem.entrySet())
			{
				index.addProperty(Integer.toString(entry.getKey()), entry.getValue());
			}
		}

		Path tempFile = cacheDirectory.resolve(INDEX_FILE + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8))
		{
			gson.toJson(index, writer);
		}
		Files.move(tempFile, cacheDirectory.resolve(INDEX_FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private void removeIndex(int itemId) throws IOException
	{
		synchronized (this)
		{
			cacheKeysByItem.remove(itemId);
		}
		writeIndex();
	}

	private Path cachePath(String cacheKey)
	{
		return cacheDirectory.resolve(cacheKey + ".json");
	}

	private static boolean isValidCacheKey(String cacheKey)
	{
		return cacheKey != null && !cacheKey.isEmpty() && cacheKey.matches("[A-Za-z0-9._-]+");
	}
}
