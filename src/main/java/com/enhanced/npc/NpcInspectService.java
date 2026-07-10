package com.enhanced.npc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.client.RuneLite;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class NpcInspectService
{
	private static final HttpUrl DEFAULT_WIKI_BASE = HttpUrl.get("https://oldschool.runescape.wiki");
	private static final String USER_AGENT = "Enhanced RuneLite plugin (https://github.com/runelite/plugin-hub)";

	private final OkHttpClient httpClient;
	private final OkHttpClient noRedirectHttpClient;
	private final Gson gson;
	private final HttpUrl wikiBase;
	private final NpcInspectParser parser = new NpcInspectParser();
	private final NpcInspectCache cache;

	@Inject
	NpcInspectService(OkHttpClient httpClient, Gson gson)
	{
		this(httpClient, gson, DEFAULT_WIKI_BASE,
			RuneLite.RUNELITE_DIR.toPath().resolve("enhanced").resolve("npc-inspect"));
	}

	NpcInspectService(OkHttpClient httpClient, Gson gson, HttpUrl wikiBase, Path cacheDirectory)
	{
		this.httpClient = httpClient;
		this.noRedirectHttpClient = httpClient.newBuilder().followRedirects(false).build();
		this.gson = gson;
		this.wikiBase = wikiBase;
		this.cache = new NpcInspectCache(gson, cacheDirectory);
	}

	public void startUp(boolean clearCache)
	{
		cache.startUp(clearCache);
	}

	public void shutDown()
	{
		cache.shutDown();
	}

	public void clearCacheAsync()
	{
		cache.clearAsync();
	}

	public CompletableFuture<NpcCombatInfo> inspect(NPC npc, int ttlDays)
	{
		if (npc == null)
		{
			return CompletableFuture.completedFuture(null);
		}

		NPCComposition composition = npc.getTransformedComposition();
		if (composition == null || composition.getName() == null)
		{
			return CompletableFuture.completedFuture(null);
		}

		return inspect(composition.getId(), composition.getName(), ttlDays);
	}

	public CompletableFuture<NpcCombatInfo> inspect(int npcId, String npcName, int ttlDays)
	{
		if (npcId < 0 || npcName == null || npcName.trim().isEmpty())
		{
			return CompletableFuture.completedFuture(null);
		}

		long now = System.currentTimeMillis() / 1000L;
		return cache.get(npcId, now, ttlDays)
			.thenCompose(cached -> cached.map(CompletableFuture::completedFuture).orElseGet(() -> fetch(npcId, npcName)));
	}

	public CompletableFuture<NpcCombatInfo> search(String query)
	{
		if (query == null || query.trim().isEmpty())
		{
			return CompletableFuture.completedFuture(null);
		}

		return searchPage(query.trim())
			.thenCompose(page ->
			{
				if (page == null)
				{
					return CompletableFuture.completedFuture(null);
				}

					NpcWikiLookup lookup = new NpcWikiLookup(page, null, wikiBase.newBuilder()
					.addPathSegment("w")
					.addPathSegment(page)
					.build()
					.toString());

				return fetchWikitext(lookup)
					.thenApply(wikitext -> parser.parse(-1, query.trim(), lookup, wikitext))
					.thenCompose(info ->
					{
						if (info == null)
						{
							return CompletableFuture.completedFuture(null);
						}

						if (info.getNpcId() < 0)
						{
							return CompletableFuture.completedFuture(info);
						}

						return cache.put(info).thenApply(ignored -> info);
					});
			});
	}

	private CompletableFuture<NpcCombatInfo> fetch(int npcId, String npcName)
	{
		return resolveLookup(npcId, npcName)
			.thenCompose(lookup ->
			{
				if (lookup == null)
				{
					return CompletableFuture.completedFuture(null);
				}
				return fetchWikitext(lookup)
					.thenApply(wikitext -> parser.parse(npcId, npcName, lookup, wikitext))
					.thenCompose(info ->
					{
						if (info == null)
						{
							return CompletableFuture.completedFuture(null);
						}
						return cache.put(info).thenApply(ignored -> info);
					});
			});
	}

	private CompletableFuture<String> searchPage(String query)
	{
		HttpUrl url = wikiBase.newBuilder()
			.addPathSegment("api.php")
			.addQueryParameter("action", "query")
			.addQueryParameter("format", "json")
			.addQueryParameter("list", "search")
			.addQueryParameter("srnamespace", "0")
			.addQueryParameter("srlimit", "1")
			.addQueryParameter("srsearch", query)
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		return execute(httpClient, request).thenApply(response ->
		{
			try (Response closeable = response; ResponseBody body = closeable.body())
			{
				if (body == null)
				{
					throw new IllegalStateException("Wiki search response did not include a body");
				}

				JsonObject json = gson.fromJson(body.string(), JsonObject.class);
				JsonArray search = json.getAsJsonObject("query").getAsJsonArray("search");
				if (search == null || search.size() == 0)
				{
					return null;
				}
				return search.get(0).getAsJsonObject().get("title").getAsString();
			}
			catch (IOException ex)
			{
				throw new IllegalStateException("Unable to read wiki search response", ex);
			}
		});
	}

	private CompletableFuture<NpcWikiLookup> resolveLookup(int npcId, String npcName)
	{
		HttpUrl url = wikiBase.newBuilder()
			.addPathSegments("w/Special:Lookup")
			.addQueryParameter("type", "npc")
			.addQueryParameter("id", Integer.toString(npcId))
			.addQueryParameter("name", npcName)
			.addQueryParameter("utm_source", "runelite")
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		return execute(noRedirectHttpClient, request).thenApply(response ->
		{
			try (Response closeable = response)
			{
				String location = closeable.header("Location");
				if (location == null)
				{
					log.debug("Wiki lookup for {} ({}) did not return a redirect", npcName, npcId);
					return null;
				}

					HttpUrl resolved = closeable.request().url().resolve(location);
				if (resolved == null)
				{
					return null;
				}

				String encodedPage = resolved.encodedPath();
				String page = encodedPage.startsWith("/w/") ? encodedPage.substring(3) : encodedPage;
				page = URLDecoder.decode(page, StandardCharsets.UTF_8);
				String anchor = resolved.fragment();
				return new NpcWikiLookup(page, anchor, resolved.toString());
			}
		});
	}

	private CompletableFuture<String> fetchWikitext(NpcWikiLookup lookup)
	{
		HttpUrl url = wikiBase.newBuilder()
			.addPathSegment("api.php")
			.addQueryParameter("action", "parse")
			.addQueryParameter("format", "json")
			.addQueryParameter("page", lookup.getPage())
			.addQueryParameter("prop", "wikitext")
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		return execute(httpClient, request).thenApply(response ->
		{
			try (Response closeable = response; ResponseBody body = closeable.body())
			{
				if (body == null)
				{
					throw new IllegalStateException("Wiki response did not include a body");
				}

				JsonObject json = gson.fromJson(body.string(), JsonObject.class);
				return json.getAsJsonObject("parse")
					.getAsJsonObject("wikitext")
					.get("*")
					.getAsString();
			}
			catch (IOException ex)
			{
				throw new IllegalStateException("Unable to read wiki response", ex);
			}
		});
	}

	private static CompletableFuture<Response> execute(OkHttpClient client, Request request)
	{
		CompletableFuture<Response> future = new CompletableFuture<>();
		client.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				future.completeExceptionally(e);
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				if (!response.isSuccessful() && response.code() / 100 != 3)
				{
					try (Response closeable = response)
					{
						future.completeExceptionally(new IOException("Unexpected wiki response: " + closeable.code()));
					}
					return;
				}

				future.complete(response);
			}
		});
		return future;
	}
}
