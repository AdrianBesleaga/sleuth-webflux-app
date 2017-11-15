package com.example.demo;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.cloud.sleuth.annotation.SpanTag;
import org.springframework.cloud.sleuth.annotation.TagValueResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootApplication
@EnableAsync
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	// the same would work for
	// RestTemplate
	// RestTemplateBuilder [Edgware]
	// WebClient [Finchley]
	@Bean WebClient.Builder webClient() {
		return WebClient.builder();
	}

	// span adjusters [Dalston]
	@Bean SpanAdjuster mySpanAdjuster() {
		return span -> {
			String path = span.tags().get("http.path");
			if (path != null && path.equals("/s1p")) {
				return span.toBuilder().name("hacked!").build();
			}
			return span;
		};
	}

	// Custom span resolver [Dalston]
	@Bean
	public TagValueResolver myCustomTagValueResolver() {
		return parameter -> {
			if (parameter instanceof Foo) {
				return ((Foo) parameter).uuid.toString();
			}
			return parameter.toString();
		};
	}
}

@RestController
class S1PController {

	private final MyService myService;
	private final Tracer tracer;

	S1PController(MyService myService, Tracer tracer) {
		this.myService = myService;
		this.tracer = tracer;
	}

	@GetMapping("/s1p") Mono<String> s1p() throws Exception {
		// Baggage [Dalston]
		tracer.getCurrentSpan().setBaggageItem("baggage", "s1p");
		// annotations [Dalston]
		myService.newSpan("hello");
		myService.continueSpan(new Foo());
		myService.async();
		return myService.callService1();
	}
}

@Service
class MyService {

	private static final Logger log = LoggerFactory.getLogger(MyService.class);

	private final WebClient webClient;

	MyService(WebClient.Builder webClientBuilder) {
		this.webClient = webClientBuilder
				.baseUrl("http://localhost:8081")
				.build();
	}

	// annotation [Dalston]
	@NewSpan("surprise")
	public void newSpan(@SpanTag("new_span_tag") String tag) throws Exception {
		log.info("Should start a new span with tag [new_span_tag -> {}]", tag);
		Thread.sleep(100);
	}

	// annotation [Dalston]
	@ContinueSpan(log = "very_important_method")
	public void continueSpan(@SpanTag(value = "continued_span", resolver = TagValueResolver.class) Foo tag) throws Exception {
		log.info("Should continue span with tag [continued_span -> {}]", tag.uuid.toString());
		Thread.sleep(200);
	}

	// WebClient [Finchley]
	public Mono<String> callService1() {
		return webClient.get().uri("/start")
				.retrieve()
				.bodyToMono(String.class);
	}

	@Async
	@SpanName("i_am_async")
	public void async() throws Exception {
		log.info("I'm async");
		Thread.sleep(300);
	}
}

class Foo {
	UUID uuid = UUID.randomUUID();
}
