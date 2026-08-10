package com.los.core.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentRoleTest {

    @Test
    void fromStringAcceptsCodeAndName() {
        assertEquals(AssignmentRole.CREDIT_OFFICER, AssignmentRole.fromString("CREDIT_OFFICER"));
        assertEquals(AssignmentRole.SALES_OFFICER, AssignmentRole.fromString("sales_officer"));
        assertEquals(AssignmentRole.RELATIONSHIP_MANAGER, AssignmentRole.fromString("RELATIONSHIP_MANAGER"));
        assertEquals(AssignmentRole.RELATIONSHIP_MANAGER, AssignmentRole.fromString("relationship_manager"));
    }

    @Test
    void isValidFalseForOther() {
        assertNull(AssignmentRole.fromString("NOT_A_ROLE"));
        assertTrue(AssignmentRole.isValid("CREDIT_MANAGER"));
    }

    @Test
    void displayLabelNonBlank() {
        for (AssignmentRole r : AssignmentRole.values()) {
            assertNotNull(r.getDisplayLabel());
            assertTrue(r.getDisplayLabel().length() > 1);
        }
    }
}
