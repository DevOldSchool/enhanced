package com.inspect.npc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.inspect.testutil.QueuedResponseInterceptor;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NpcInspectServiceTest
{
	private static final HttpUrl WIKI_BASE = HttpUrl.get("https://wiki.test/");

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private NpcInspectService service;

	@After
	public void tearDown()
	{
		if (service != null)
		{
			service.shutDown();
		}
	}

	@Test
	public void invalidInspectAndSearchInputDoNotMakeHttpRequest() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		NpcInspectService npcService = service(responses);

		assertNull(npcService.inspect((net.runelite.api.NPC) null, 7).get(5, TimeUnit.SECONDS));
		assertNull(npcService.inspect(-1, "Goblin", 7).get(5, TimeUnit.SECONDS));
		assertNull(npcService.inspect(3028, null, 7).get(5, TimeUnit.SECONDS));
		assertNull(npcService.inspect(3028, "  ", 7).get(5, TimeUnit.SECONDS));
		assertNull(npcService.search(null).get(5, TimeUnit.SECONDS));
		assertEquals(0, responses.requestCount());
	}

	@Test
	public void resolvesBucketLookupAndCachesSuccessfulInspect() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		responses.enqueue(200, bucketResponse("Goblin guard", "Level 2"));
		responses.enqueue(200, parseResponse("{{Infobox Monster\n"
			+ "|name = Goblin guard\n"
			+ "|combat = 2\n"
			+ "|id = 3028\n"
			+ "|dcrush = 5\n"
			+ "}}"));
		NpcInspectService npcService = service(responses);

		NpcCombatInfo first = npcService.inspect(3028, "Goblin guard", 7).get(5, TimeUnit.SECONDS);
		NpcCombatInfo second = npcService.inspect(3028, "Goblin guard", 7).get(5, TimeUnit.SECONDS);

		assertEquals("Goblin_guard", first.getWikiPage());
		assertEquals("Level_2", first.getWikiAnchor());
		assertEquals("Goblin guard", first.getDisplayName());
		assertEquals("5", first.getCrushDefence());
		assertEquals(first, second);
		assertEquals(2, responses.requestCount());
		assertEquals("/api.php", responses.requests().get(0).url().encodedPath());
		assertEquals("bucket", responses.requests().get(0).url().queryParameter("action"));
		assertTrue(responses.requests().get(0).url().queryParameter("query").contains("infobox_monster"));
		assertTrue(responses.requests().get(0).url().queryParameter("query").contains("3028"));
		assertEquals("/api.php", responses.requests().get(1).url().encodedPath());
		assertEquals("parse", responses.requests().get(1).url().queryParameter("action"));
		assertEquals("Goblin_guard", responses.requests().get(1).url().queryParameter("page"));
	}

	@Test
	public void searchUsesCachedNpcInfoForRepeatedQuery() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		responses.enqueue(200, searchResponse("Goblin guard"));
		responses.enqueue(200, parseResponse("{{Infobox Monster\n"
			+ "|name = Goblin guard\n"
			+ "|combat = 2\n"
			+ "|id = 3028\n"
			+ "|dcrush = 5\n"
			+ "}}"));
		NpcInspectService npcService = service(responses);

		NpcCombatInfo first = npcService.search("Goblin guard", 7).get(5, TimeUnit.SECONDS);
		NpcCombatInfo second = npcService.search("Goblin guard", 7).get(5, TimeUnit.SECONDS);

		assertEquals("Goblin guard", first.getDisplayName());
		assertEquals(first, second);
		assertEquals(2, responses.requestCount());
		assertEquals("query", responses.requests().get(0).url().queryParameter("action"));
		assertEquals("parse", responses.requests().get(1).url().queryParameter("action"));
	}

	@Test
	public void searchUsesPersistedCachedNpcInfoByName() throws Exception
	{
		Path cacheDirectory = temporaryFolder.newFolder().toPath();
		QueuedResponseInterceptor firstResponses = new QueuedResponseInterceptor();
		firstResponses.enqueue(200, searchResponse("Goblin guard"));
		firstResponses.enqueue(200, parseResponse("{{Infobox Monster\n"
			+ "|name = Goblin guard\n"
			+ "|combat = 2\n"
			+ "|id = 3028\n"
			+ "|dcrush = 5\n"
			+ "}}"));
		NpcInspectService firstService = service(firstResponses, cacheDirectory);
		NpcCombatInfo first = firstService.search("Goblin guard", 7).get(5, TimeUnit.SECONDS);
		firstService.shutDown();
		service = null;

		QueuedResponseInterceptor secondResponses = new QueuedResponseInterceptor();
		NpcInspectService secondService = service(secondResponses, cacheDirectory);
		NpcCombatInfo second = secondService.search("Goblin_guard", 7).get(5, TimeUnit.SECONDS);

		assertEquals(first, second);
		assertEquals(0, secondResponses.requestCount());
	}

	@Test
	public void emptySearchResultReturnsNull() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		responses.enqueue(200, "{\"query\":{\"search\":[]}}");
		NpcInspectService npcService = service(responses);

		assertNull(npcService.search("missing npc").get(5, TimeUnit.SECONDS));
		assertEquals(1, responses.requestCount());
		assertEquals("missing npc", responses.requests().get(0).url().queryParameter("srsearch"));
	}

	@Test
	public void httpAndTransportFailuresCompleteExceptionally() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		responses.enqueue(500, "server error");
		NpcInspectService httpFailureService = service(responses);

		assertFailure(() -> httpFailureService.search("goblin").get(5, TimeUnit.SECONDS), "Unexpected wiki response: 500");

		service.shutDown();
		service = null;
		QueuedResponseInterceptor transportFailure = new QueuedResponseInterceptor();
		transportFailure.enqueueFailure(new IOException("offline"));
		NpcInspectService transportFailureService = service(transportFailure);
		assertFailure(() -> transportFailureService.search("goblin").get(5, TimeUnit.SECONDS), "offline");
	}

	private NpcInspectService service(QueuedResponseInterceptor responses) throws IOException
	{
		return service(responses, temporaryFolder.newFolder().toPath());
	}

	private NpcInspectService service(QueuedResponseInterceptor responses, Path cacheDirectory)
	{
		service = new NpcInspectService(
			new OkHttpClient.Builder().addInterceptor(responses).build(),
			new Gson(),
			WIKI_BASE,
			cacheDirectory);
		return service;
	}

	private static String parseResponse(String wikitext)
	{
		return new Gson().toJson(new ParseResponse(wikitext));
	}

	private static String bucketResponse(String name, String anchor)
	{
		return "{\"bucket\":[{\"name\":\""
			+ name
			+ "\",\"version_anchor\":\""
			+ anchor
			+ "\"}]}";
	}

	private static String searchResponse(String title)
	{
		return "{\"query\":{\"search\":[{\"title\":\"" + title + "\"}]}}";
	}

	private static void assertFailure(CheckedAction action, String expectedMessage) throws Exception
	{
		try
		{
			action.run();
			fail("Expected lookup to fail");
		}
		catch (ExecutionException ex)
		{
			assertTrue(ex.getCause() instanceof IOException);
			assertTrue(ex.getCause().getMessage().contains(expectedMessage));
		}
	}

	private interface CheckedAction
	{
		void run() throws Exception;
	}

	private static final class ParseResponse
	{
		private final Parse parse;

		private ParseResponse(String wikitext)
		{
			this.parse = new Parse(wikitext);
		}
	}

	private static final class Parse
	{
		private final Wikitext wikitext;

		private Parse(String value)
		{
			this.wikitext = new Wikitext(value);
		}
	}

	private static final class Wikitext
	{
		@SerializedName("*")
		private final String value;

		private Wikitext(String value)
		{
			this.value = value;
		}
	}
}
