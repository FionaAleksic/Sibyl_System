package org.sibyl.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AddonMigrationRunnerTest {
  @Test void schemaIsFixedAndNamespaced() {
    assertEquals("sibyl_ext_inventory",AddonMigrationRunner.schema("Sibyl.inventory"));
    assertEquals("sibyl_ext_report_book",AddonMigrationRunner.schema("Sibyl.report-book"));
    assertThrows(IllegalArgumentException.class,()->AddonMigrationRunner.schema("core"));
  }
}
