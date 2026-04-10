# AGENTS.md — Spring AI Nutrition Planner

## Framework

- **Spring AI 2.0** with provider-specific starters (`spring-ai-openai-spring-boot-starter`, etc.)
- Auto-configuration via Spring Boot properties under `spring.ai.*`

## Key Patterns

### ChatClient

Use the autoconfigured `ChatClient.Builder` to build chat interactions. Prefer the fluent API:

```java
@Service
public class NutritionAdvisor {
    private final ChatClient chatClient;

    public NutritionAdvisor(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a nutrition expert...")
            .build();
    }

    public String analyzeMeal(String description) {
        return chatClient.prompt()
            .user(description)
            .call()
            .content();
    }
}
```

### Structured Output

Map AI responses directly to Java records using Spring AI's output converters:

```java
public record MealPlan(List<Meal> meals, int totalCalories) {}

var plan = chatClient.prompt()
    .user("Create a 2000 calorie meal plan")
    .call()
    .entity(MealPlan.class);
```

### Function Calling

Register functions as `@Bean` definitions. Spring AI handles tool schema generation and invocation:

```java
@Bean
@Description("Look up nutritional info for a food item")
public Function<FoodQuery, NutrientInfo> nutritionLookup(NutrientRepository repo) {
    return query -> repo.findByName(query.foodName());
}
```

### RAG & Vector Stores

- Use autoconfigured `VectorStore` beans (PGVector, Redis, Pinecone, etc.).
- Load documents with `DocumentReader` implementations and store embeddings via `VectorStore.add()`.
- Use `QuestionAnswerAdvisor` to wire RAG into the chat pipeline.

### Observability

Spring AI 2.0 integrates with Micrometer for metrics and tracing out of the box. Use the `spring-boot-starter-opentelemetry` for full trace propagation through AI calls.

### Testing

- Use `MockChatModel` or mock `ChatClient` responses in unit tests.
- Integration tests should use Testcontainers or a local model (e.g., Ollama) — never call live APIs in CI.
- Use `@SpringBootTest` for full context tests, `@MockBean` for isolated service tests.

## Dependencies

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```
