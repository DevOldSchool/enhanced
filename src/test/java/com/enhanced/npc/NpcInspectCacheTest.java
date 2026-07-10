package com.enhanced.npc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NpcInspectCacheTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void readsFreshDiskCache() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);
		NpcCombatInfo info = info(3028, 1000L);

		cache.put(info).get();
		cache.shutDown();

		NpcInspectCache restored = new NpcInspectCache(new Gson(), directory);
		Optional<NpcCombatInfo> cached = restored.get(3028, 1100L, 7).get();

		assertTrue(cached.isPresent());
		assertEquals(3028, cached.get().getNpcId());
		restored.shutDown();
	}

	@Test
	public void keepsCacheAtExactTtlBoundaryAndExpiresItAfterward() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);
		long sevenDays = 7L * 24L * 60L * 60L;
		cache.put(info(3028, 1000L)).get();

		assertTrue(cache.get(3028, 1000L + sevenDays, 7).get().isPresent());
		assertFalse(cache.get(3028, 1000L + sevenDays + 1L, 7).get().isPresent());
		cache.shutDown();
	}

	@Test
	public void zeroTtlNeverReturnsCachedEntry() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);
		cache.put(info(3028, 1000L)).get();

		assertFalse(cache.get(3028, 1000L, 0).get().isPresent());
		cache.shutDown();
	}

	@Test
	public void ignoresExpiredDiskCache() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);
		NpcCombatInfo info = info(3028, 1000L);

		cache.put(info).get();
		Optional<NpcCombatInfo> cached = cache.get(3028, 1000L + 8L * 24L * 60L * 60L, 7).get();

		assertFalse(cached.isPresent());
		cache.shutDown();
	}

	@Test
	public void ignoresCorruptDiskCache() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);
		NpcCombatInfo info = info(3028, 1000L);
		cache.put(info).get();
		cache.shutDown();

		Files.write(directory.resolve(info.cacheKey() + ".json"), "{".getBytes(StandardCharsets.UTF_8));

		NpcInspectCache restored = new NpcInspectCache(new Gson(), directory);
		Optional<NpcCombatInfo> cached = restored.get(3028, 1100L, 7).get();

		assertFalse(cached.isPresent());
		restored.shutDown();
	}

	@Test
	public void rejectsDiskPayloadForDifferentNpc() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		Gson gson = new Gson();
		NpcCombatInfo expected = info(3028, 1000L);
		NpcInspectCache cache = new NpcInspectCache(gson, directory);
		cache.put(expected).get();
		cache.shutDown();

		Path payload = directory.resolve(expected.cacheKey() + ".json");
		Files.write(payload, gson.toJson(info(9999, 1000L)).getBytes(StandardCharsets.UTF_8));

		NpcInspectCache restored = new NpcInspectCache(gson, directory);
		Optional<NpcCombatInfo> cached = restored.get(3028, 1100L, 7).get();

		assertFalse(cached.isPresent());
		assertFalse(Files.exists(payload));
		restored.shutDown();
	}

	@Test
	public void ignoresEntireIndexWhenAnyEntryIsCorrupt() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		NpcCombatInfo info = info(3028, 1000L);
		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);
		cache.put(info).get();
		cache.shutDown();

		Path index = directory.resolve("index.json");
		String corruptIndex = "{\"3028\":\"" + info.cacheKey() + "\",\"not-an-id\":\"bad\"}";
		Files.write(index, corruptIndex.getBytes(StandardCharsets.UTF_8));

		NpcInspectCache restored = new NpcInspectCache(new Gson(), directory);
		Optional<NpcCombatInfo> cached = restored.get(3028, 1100L, 7).get();

		assertFalse(cached.isPresent());
		assertFalse(Files.exists(index));
		restored.shutDown();
	}

	@Test
	public void rejectsCacheKeyThatEscapesCacheDirectory() throws Exception
	{
		Path parent = temporaryFolder.newFolder().toPath();
		Path directory = Files.createDirectory(parent.resolve("cache"));
		Path outsideFile = parent.resolve("outside.json");
		Files.write(outsideFile, new Gson().toJson(info(3028, 1000L)).getBytes(StandardCharsets.UTF_8));
		Path indexFile = directory.resolve("index.json");
		Files.write(indexFile, "{\"3028\":\"../outside\"}".getBytes(StandardCharsets.UTF_8));

		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);

		assertFalse(cache.get(3028, 1100L, 7).get().isPresent());
		assertFalse(Files.exists(indexFile));
		assertTrue(Files.exists(outsideFile));
		cache.shutDown();
	}

	@Test
	public void clearRemovesMemoryAndDiskEntries() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		NpcCombatInfo info = info(3028, 1000L);
		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);
		cache.put(info).get();

		cache.clearAsync().get();

		assertFalse(cache.get(3028, 1100L, 7).get().isPresent());
		assertFalse(Files.exists(directory.resolve(info.cacheKey() + ".json")));
		assertFalse(Files.exists(directory.resolve("index.json")));
		cache.shutDown();
	}

	@Test
	public void startupClearRunsBeforeSubsequentCacheReads() throws Exception
	{
		Path directory = temporaryFolder.newFolder().toPath();
		NpcCombatInfo info = info(3028, 1000L);
		NpcInspectCache cache = new NpcInspectCache(new Gson(), directory);
		cache.put(info).get();
		cache.shutDown();

		NpcInspectCache restored = new NpcInspectCache(new Gson(), directory);
		restored.startUp(true);

		assertFalse(restored.get(3028, 1100L, 7).get().isPresent());
		assertFalse(Files.exists(directory.resolve(info.cacheKey() + ".json")));
		assertFalse(Files.exists(directory.resolve("index.json")));
		restored.shutDown();
	}

	private static NpcCombatInfo info(int npcId, long fetchedAt)
	{
		return NpcCombatInfo.builder()
			.npcId(npcId)
			.wikiPage("Goblin")
			.wikiAnchor("Level_2")
			.displayName("Goblin")
			.fetchedAtEpochSecond(fetchedAt)
			.sourceUrl("https://oldschool.runescape.wiki/w/Goblin#Level_2")
			.build();
	}
}
