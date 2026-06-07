package dev.antonlammers.macrotrac.ui.addfood

import dev.antonlammers.macrotrac.domain.model.MealCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class MealCategoryForHourTest {

    @Test fun `before 5 is snack`()      { assertEquals(MealCategory.SNACK,     mealCategoryForHour(3)) }
    @Test fun `5 is breakfast`()         { assertEquals(MealCategory.BREAKFAST,  mealCategoryForHour(5)) }
    @Test fun `9 is breakfast`()         { assertEquals(MealCategory.BREAKFAST,  mealCategoryForHour(9)) }
    @Test fun `10 is lunch`()            { assertEquals(MealCategory.LUNCH,      mealCategoryForHour(10)) }
    @Test fun `13 is lunch`()            { assertEquals(MealCategory.LUNCH,      mealCategoryForHour(13)) }
    @Test fun `14 is snack`()            { assertEquals(MealCategory.SNACK,      mealCategoryForHour(14)) }
    @Test fun `16 is snack`()            { assertEquals(MealCategory.SNACK,      mealCategoryForHour(16)) }
    @Test fun `17 is dinner`()           { assertEquals(MealCategory.DINNER,     mealCategoryForHour(17)) }
    @Test fun `21 is dinner`()           { assertEquals(MealCategory.DINNER,     mealCategoryForHour(21)) }
    @Test fun `22 is snack`()            { assertEquals(MealCategory.SNACK,      mealCategoryForHour(22)) }
    @Test fun `midnight is snack`()      { assertEquals(MealCategory.SNACK,      mealCategoryForHour(0)) }
}
