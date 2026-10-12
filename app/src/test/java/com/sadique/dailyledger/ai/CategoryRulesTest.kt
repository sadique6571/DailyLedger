package com.sadique.dailyledger.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRulesTest {
    @Test fun recognizesSpecificSpicesAndLaundryItems() {
        assertEquals("Garam masala", CategoryRules.infer("EXPENSE", "garam masala 80"))
        assertEquals("Ground coriander", CategoryRules.infer("EXPENSE", "sukha dhaniya 50"))
        assertEquals("Laundry detergent", CategoryRules.infer("EXPENSE", "surf 250"))
    }

    @Test fun keepsMedicalEducationAndDaughterTravelCostsSeparate() {
        assertEquals("Father medical", CategoryRules.infer("EXPENSE", "father medical expenses 2000"))
        assertEquals("Mother medical", CategoryRules.infer("EXPENSE", "ammi ki dawa 500"))
        assertEquals("Wife medical", CategoryRules.infer("EXPENSE", "wife doctor 800"))
        assertEquals("Baby medical", CategoryRules.infer("EXPENSE", "baby medicine 600"))
        assertEquals("Son medical", CategoryRules.infer("EXPENSE", "son medical 1000"))
        assertEquals("Daughter medical", CategoryRules.infer("EXPENSE", "daughter doctor 1000"))
        assertEquals("Son fees", CategoryRules.infer("EXPENSE", "son fee 5000"))
        assertEquals("Daughter fees", CategoryRules.infer("EXPENSE", "daughter fee 5000"))
        assertEquals("Daughter travel", CategoryRules.infer("EXPENSE", "daughter trip 2500"))
    }
}
