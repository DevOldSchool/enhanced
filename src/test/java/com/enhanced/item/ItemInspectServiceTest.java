package com.enhanced.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.enhanced.testutil.QueuedResponseInterceptor;
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

public class ItemInspectServiceTest
{
	private static final HttpUrl WIKI_BASE = HttpUrl.get("https://wiki.test/");

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private ItemInspectService service;

	@After
	public void tearDown()
	{
		if (service != null)
		{
			service.shutDown();
		}
	}

	@Test
	public void invalidInspectInputDoesNotMakeHttpRequest() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		ItemInspectService itemService = service(responses);

		assertNull(itemService.inspect(-1, "Whip", 7).get(5, TimeUnit.SECONDS));
		assertNull(itemService.inspect(4151, null, 7).get(5, TimeUnit.SECONDS));
		assertNull(itemService.inspect(4151, "  ", 7).get(5, TimeUnit.SECONDS));
		assertNull(itemService.search("  ").get(5, TimeUnit.SECONDS));
		assertEquals(0, responses.requestCount());
	}

	@Test
	public void resolvesRelativeRedirectAndCachesSuccessfulInspect() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		responses.enqueueRedirect("/w/Abyssal_whip#Variant");
		responses.enqueue(200, parseResponse("{{Infobox Item\n"
			+ "|name = Abyssal whip\n"
			+ "|id = 4151\n"
			+ "}}\n"
			+ "{{Infobox Bonuses\n"
			+ "|slot = weapon\n"
			+ "|aslash = +82\n"
			+ "}}"));
		ItemInspectService itemService = service(responses);

		ItemInspectInfo first = itemService.inspect(4151, "Abyssal whip", 7).get(5, TimeUnit.SECONDS);
		ItemInspectInfo second = itemService.inspect(4151, "Abyssal whip", 7).get(5, TimeUnit.SECONDS);

		assertEquals("Abyssal_whip", first.getWikiPage());
		assertEquals("Variant", first.getWikiAnchor());
		assertEquals("https://wiki.test/w/Abyssal_whip#Variant", first.getSourceUrl());
		assertEquals("+82", first.getAttackSlash());
		assertEquals(first, second);
		assertEquals(2, responses.requestCount());
		assertEquals("/w/Special:Lookup", responses.requests().get(0).url().encodedPath());
		assertEquals("item", responses.requests().get(0).url().queryParameter("type"));
		assertEquals("4151", responses.requests().get(0).url().queryParameter("id"));
		assertEquals("Abyssal whip", responses.requests().get(0).url().queryParameter("name"));
		assertEquals("/api.php", responses.requests().get(1).url().encodedPath());
		assertEquals("parse", responses.requests().get(1).url().queryParameter("action"));
		assertEquals("Abyssal_whip", responses.requests().get(1).url().queryParameter("page"));
	}

	@Test
	public void emptySearchResultReturnsNull() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		responses.enqueue(200, "{\"query\":{\"search\":[]}}");
		ItemInspectService itemService = service(responses);

		assertNull(itemService.search("missing item").get(5, TimeUnit.SECONDS));
		assertEquals(1, responses.requestCount());
		assertEquals("missing item", responses.requests().get(0).url().queryParameter("srsearch"));
	}

	@Test
	public void httpAndTransportFailuresCompleteExceptionally() throws Exception
	{
		QueuedResponseInterceptor responses = new QueuedResponseInterceptor();
		responses.enqueue(500, "server error");
		ItemInspectService itemService = service(responses);
		ItemInspectService httpFailureService = itemService;

		assertFailure(() -> httpFailureService.search("whip").get(5, TimeUnit.SECONDS), "Unexpected wiki response: 500");

		service.shutDown();
		service = null;
		QueuedResponseInterceptor transportFailure = new QueuedResponseInterceptor();
		transportFailure.enqueueFailure(new IOException("offline"));
		itemService = service(transportFailure);
		ItemInspectService finalItemService = itemService;
		assertFailure(() -> finalItemService.search("whip").get(5, TimeUnit.SECONDS), "offline");
	}

	private ItemInspectService service(QueuedResponseInterceptor responses) throws IOException
	{
		Path cacheDirectory = temporaryFolder.newFolder().toPath();
		service = new ItemInspectService(
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
