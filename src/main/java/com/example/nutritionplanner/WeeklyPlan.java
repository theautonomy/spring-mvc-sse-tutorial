package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record WeeklyPlan(List<DailyPlan> days) {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    @Tool(description = "Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        // distinct() because the model may return the same day more than once; nutritionTotalsForDay sums all of them
        var dailyNutritionTotals = days.stream().map(DailyPlan::day).distinct().collect(Collectors.toMap(
                day -> day, this::nutritionTotalsForDay, (a, _) -> a, () -> new EnumMap<>(DayOfWeek.class)
        ));
        log.info("WeeklyPlan:dailyNutritionTotals tool method finished with {}", dailyNutritionTotals);
        return dailyNutritionTotals;
    }

    @Tool(description = "Returns the total calories, protein, carbs, fat, and sodium for a specific day of the weekly meal plan")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day) {
        var nutritionInfo = new NutritionInfo(days.stream()
                .filter(d -> d.day() == day)
                .flatMap(WeeklyPlan::meals)
                .toList());
        log.info("WeeklyPlan:nutritionTotalsForDay tool method finished with {} for {}", nutritionInfo, day);
        return nutritionInfo;
    }

    @Tool(description = "Returns the total number of meals across all days of the weekly meal plan")
    public long totalMealCount() {
        var count = days.stream().flatMap(WeeklyPlan::meals).count();
        log.info("WeeklyPlan:totalMealCount tool method finished with {}", count);
        return count;
    }

    private static Stream<Recipe> meals(DailyPlan dailyPlan) {
        return Stream.of(dailyPlan.breakfast(), dailyPlan.lunch(), dailyPlan.dinner()).filter(Objects::nonNull);
    }

    public record DailyPlan(DayOfWeek day, @Nullable Recipe breakfast, @Nullable Recipe lunch, @Nullable Recipe dinner) {}
}
