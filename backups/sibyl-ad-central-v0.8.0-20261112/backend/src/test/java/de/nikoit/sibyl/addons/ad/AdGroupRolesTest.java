package de.nikoit.sibyl.addons.ad;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
class AdGroupRolesTest {
  private static final String ROOT=",OU=Groups,DC=example,DC=org";
  private static final String ADMIN="CN=IT-Admins"+ROOT;
  private static final String USER="CN=Employees"+ROOT;
  @Test void userGroup() {
    assertEquals("USER",AdGroupRoles.roleForGroupDns(Set.of(USER),ADMIN,USER));
  }
  @Test void adminGroup() {
    assertEquals("ADMIN",AdGroupRoles.roleForGroupDns(Set.of(ADMIN),ADMIN,USER));
  }
  @Test void overlappingUserWins() {
    assertEquals("USER",AdGroupRoles.roleForGroupDns(Set.of(ADMIN,USER),ADMIN,USER));
  }
  @Test void caseInsensitive() {
    assertEquals("ADMIN",AdGroupRoles.roleForGroupDns(Set.of(ADMIN.toLowerCase()),ADMIN,USER));
  }
  @Test void unknownGroupDenied() {
    assertNull(AdGroupRoles.roleForGroupDns(Set.of("CN=Unknown"+ROOT),ADMIN,USER));
  }
  @Test void blankGroupDenied() {
    assertNull(AdGroupRoles.roleForGroupDns(Set.of(),ADMIN,USER));
  }
  @Test void sameGroupDifferentOuDenied() {
    assertNull(AdGroupRoles.roleForGroupDns(
      Set.of("CN=IT-Admins,OU=Other,DC=example,DC=org"),ADMIN,USER));
  }
}
