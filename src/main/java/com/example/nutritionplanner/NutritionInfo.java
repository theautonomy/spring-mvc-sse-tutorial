package com.example.nutritionplanner;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

record NutritionInfo (Integer calories, Double proteinGrams, Double carbGrams, Double fatGrams, Integer sodiumMg) {
        // Missing values (no nutrition or a null field in the LLM output) count as 0 instead of failing with an NPE
        NutritionInfo(List<Recipe> recipes) {
            this(sumInt(recipes, NutritionInfo::calories),
                    sumDouble(recipes, NutritionInfo::proteinGrams),
                    sumDouble(recipes, NutritionInfo::carbGrams),
                    sumDouble(recipes, NutritionInfo::fatGrams),
                    sumInt(recipes, NutritionInfo::sodiumMg)
            );
        }

        private static int sumInt(List<Recipe> recipes, Function<NutritionInfo, Integer> field) {
            return recipes.stream().map(Recipe::nutrition).filter(Objects::nonNull)
                    .map(field).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        }

        private static double sumDouble(List<Recipe> recipes, Function<NutritionInfo, Double> field) {
            return recipes.stream().map(Recipe::nutrition).filter(Objects::nonNull)
                    .map(field).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        }
}
