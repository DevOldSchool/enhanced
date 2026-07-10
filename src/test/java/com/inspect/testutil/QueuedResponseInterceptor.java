package com.inspect.testutil;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class QueuedResponseInterceptor implements Interceptor
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final Queue<ResponseStep> responses = new ArrayDeque<>();
	private final List<Request> requests = new ArrayList<>();

	public synchronized void enqueue(int code, String body)
	{
		responses.add(ResponseStep.response(code, body, null));
	}

	public synchronized void enqueueRedirect(String location)
	{
		responses.add(ResponseStep.response(302, "", location));
	}

	public synchronized void enqueueFailure(IOException failure)
	{
		responses.add(ResponseStep.failure(failure));
	}

	public synchronized int requestCount()
	{
		return requests.size();
	}

	public synchronized List<Request> requests()
	{
		return Collections.unmodifiableList(new ArrayList<>(requests));
	}

	@Override
	public synchronized Response intercept(Chain chain) throws IOException
	{
		Request request = chain.request();
		requests.add(request);
		ResponseStep step = responses.poll();
		if (step == null)
		{
			throw new IOException("Unexpected HTTP request: " + request.url());
		}
		if (step.failure != null)
		{
			throw step.failure;
		}

		Response.Builder response = new Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(step.code)
			.message(Integer.toString(step.code))
			.body(ResponseBody.create(JSON, step.body));
		if (step.location != null)
		{
			response.header("Location", step.location);
		}
		return response.build();
	}

	private static final class ResponseStep
	{
		private final int code;
		private final String body;
		private final String location;
		private final IOException failure;

		private ResponseStep(int code, String body, String location, IOException failure)
		{
			this.code = code;
			this.body = body;
			this.location = location;
			this.failure = failure;
		}

		private static ResponseStep response(int code, String body, String location)
		{
			return new ResponseStep(code, body == null ? "" : body, location, null);
		}

		private static ResponseStep failure(IOException failure)
		{
			return new ResponseStep(0, "", null, failure);
		}
	}
}
