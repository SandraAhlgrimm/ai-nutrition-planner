package com.example.nutritionplanner.patterns;

import com.example.NutritionPlannerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatternController.class)
@Import(NutritionPlannerConfiguration.class)
@ActiveProfiles("test")
class PatternProfileOffTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ApplicationContext context;

    @Test
    void profileOffRemovesAllPatternEndpointsAndBeans() throws Exception {
        mvc.perform(get("/api/patterns").with(user("alice"))).andExpect(status().isNotFound());
        for (String id : List.of("decision-routing", "confidence-cascade", "parallel-mapper")) {
            mvc.perform(post("/api/patterns/" + id).with(user("alice"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hello\",\"items\":[\"meal\"]}"))
                    .andExpect(status().isNotFound());
        }
        assertThat(context.getBeansOfType(PatternController.class)).isEmpty();
        assertThat(context.getBeansOfType(PatternLab.class)).isEmpty();
        assertThat(context.getBeansOfType(PatternConfiguration.class)).isEmpty();
    }
}
