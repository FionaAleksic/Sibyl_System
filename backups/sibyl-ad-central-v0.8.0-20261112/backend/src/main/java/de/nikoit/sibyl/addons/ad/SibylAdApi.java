package de.nikoit.sibyl.addons.ad;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public contract for AD functionality compiled into Sibyl.ad.
 * Other addons never receive LDAP contexts, bind passwords or AD connection records.
 */
public interface SibylAdApi {
    record Login(String username,String role) {}
    record Controller(String hostname,String ipv4,String domain) {}
    record GroupRoster(Map<String,Set<String>> usersByRole,int directories) {}
    record Status(boolean enabled,int activeDirectories,int loginDirectories,int syncReadyDirectories) {}

    Login authenticate(String submittedName,String password);
    /** role -> setting key within the Sibyl.ad configuration, NOT group DN. */
    GroupRoster groupRoster(Map<String,String> roleToAdAddonSetting);
    List<Controller> controllers();
    String defaultDomain();
    Status status();
}
