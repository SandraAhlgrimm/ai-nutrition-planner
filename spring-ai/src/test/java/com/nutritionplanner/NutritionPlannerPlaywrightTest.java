package com.nutritionplanner;

import com.microsoft.playwright.*;
import com.nutritionplanner.agent.NutritionGuardService;
import com.nutritionplanner.agent.RecipeCuratorService;
import com.nutritionplanner.agent.SeasonalIngredientService;
import com.nutritionplanner.model.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NutritionPlannerPlaywrightTest {

    private static final NutritionInfo NUTRITION = new NutritionInfo(500, 30.0, 60.0, 15.0, 800);

    private static final Recipe SAMPLE_RECIPE = new Recipe("Spring Asparagus Salad",
            List.of(new Recipe.Ingredient("asparagus", "200", "g"),
                    new Recipe.Ingredient("olive oil", "2", "tbsp")),
            NUTRITION, "Blanch asparagus and toss with olive oil.", 15);

    private static final WeeklyPlan MOCK_PLAN = new WeeklyPlan(List.of(
            new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, SAMPLE_RECIPE, SAMPLE_RECIPE, SAMPLE_RECIPE)
    ));

    private static final SeasonalIngredients SEASONAL = new SeasonalIngredients(
            List.of(new Recipe.Ingredient("asparagus", "1", "bunch")));

    private static final NutritionAuditValidationResult PASS_RESULT = new NutritionAuditValidationResult(
            true, List.of(), "All checks passed.");

    @LocalServerPort
    private int port;

    @MockitoBean
    private SeasonalIngredientService seasonalIngredientService;

    @MockitoBean
    private RecipeCuratorService recipeCuratorService;

    @MockitoBean
    private NutritionGuardService nutritionGuardService;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void setupBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void teardownBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void createPage() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void closePage() {
        if (context != null) context.close();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void login() {
        page.navigate(url("/login"));
        page.fill("#username", "alice");
        page.fill("#password", "123456");
        page.click("button[type=submit]");
        page.waitForURL("**/");
    }

    @Test
    void loginPageLoads() {
        page.navigate(url("/login"));

        assertThat(page.title()).contains("Sign in");
        assertThat(page.locator("#username").isVisible()).isTrue();
        assertThat(page.locator("#password").isVisible()).isTrue();
    }

    @Test
    void loginAndRedirect() {
        login();

        assertThat(page.url()).endsWith("/");
        assertThat(page.locator("nav").textContent()).contains("AI Nutrition Planner");
    }

    @Test
    void generatePlanFlow() {
        when(seasonalIngredientService.fetchSeasonalIngredients(anyString())).thenReturn(SEASONAL);
        when(recipeCuratorService.createMealPlan(anyString())).thenReturn(MOCK_PLAN);
        when(nutritionGuardService.validate(anyString())).thenReturn(PASS_RESULT);

        login();

        page.check("input[name=monday][value=BREAKFAST]");
        page.check("input[name=monday][value=LUNCH]");
        page.check("input[name=monday][value=DINNER]");
        page.fill("#countryCode", "DE");
        page.click("button[type=submit]");

        page.waitForSelector("#result h2", new Page.WaitForSelectorOptions().setTimeout(30000));

        assertThat(page.locator("#result h2").textContent()).contains("Your Weekly Plan");
        assertThat(page.locator("#result").textContent()).contains("Spring Asparagus Salad");
        assertThat(page.locator("#result").textContent()).contains("kcal");
    }

    @Test
    void toggleAllSelectsAllMeals() {
        login();

        page.locator(".day-card").first().locator("button").click();

        var checkboxes = page.locator(".day-card").first().locator("input[type=checkbox]");
        for (int i = 0; i < checkboxes.count(); i++) {
            assertThat(checkboxes.nth(i).isChecked()).isTrue();
        }
    }

    @Test
    void emptySelectionHandledGracefully() {
        when(seasonalIngredientService.fetchSeasonalIngredients(anyString())).thenReturn(SEASONAL);
        when(recipeCuratorService.createMealPlan(anyString())).thenReturn(new WeeklyPlan(List.of()));
        when(nutritionGuardService.validate(anyString())).thenReturn(PASS_RESULT);

        login();

        page.click("button[type=submit]");

        page.waitForSelector("#result", new Page.WaitForSelectorOptions().setTimeout(30000));
    }
}
