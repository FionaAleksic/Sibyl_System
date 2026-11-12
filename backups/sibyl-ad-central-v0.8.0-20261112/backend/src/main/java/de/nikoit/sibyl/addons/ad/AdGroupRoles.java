package de.nikoit.sibyl.addons.ad;

import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;

/** Role resolution from a configured directory. Never hard-code company group names. */
public final class AdGroupRoles {
    private AdGroupRoles(){}
    private static final String MATCHING_RULE_CHAIN="1.2.840.113556.1.4.1941";

    /** User groups override admin groups to prevent accidental privilege escalation. */
    public static String roleForGroupDns(Set<String> memberships,String adminGroups,String userGroups) {
        Set<String> present=new HashSet<>();
        for(String value:memberships)if(value!=null)present.add(value.toLowerCase(Locale.ROOT));
        for(String value:splitDns(userGroups))
            if(present.contains(value.toLowerCase(Locale.ROOT)))return "USER";
        for(String value:splitDns(adminGroups))
            if(present.contains(value.toLowerCase(Locale.ROOT)))return "ADMIN";
        return null;
    }
    public static String roleForUser(InitialLdapContext context,String username,
            String userBaseDn,String adminGroupDns,String userGroupDns) throws NamingException {
        SearchControls c=new SearchControls();
        c.setSearchScope(SearchControls.SUBTREE_SCOPE);
        c.setCountLimit(2);
        c.setTimeLimit(4500);
        c.setReturningAttributes(new String[]{"memberOf"});
        String filter="(&(objectCategory=person)(objectClass=user)(sAMAccountName="
            +escape(username)+"))";
        String userDn=null;
        Attributes attributes=null;
        try(NamingResults results=new NamingResults(context.search(userBaseDn,filter,c))){
            while(true){
                boolean more;
                try{more=results.rows.hasMore();}
                catch(PartialResultException e){if(userDn==null)return null;break;}
                if(!more)break;
                SearchResult user=results.rows.next();
                if(userDn!=null)return null;
                userDn=user.getNameInNamespace();
                attributes=user.getAttributes();
            }
        }
        if(userDn==null)return null;
        Set<String> direct=new HashSet<>();
        Attribute memberships=attributes==null?null:attributes.get("memberOf");
        if(memberships!=null){
            NamingEnumeration<?> vals=memberships.getAll();
            try{while(vals.hasMore())direct.add(String.valueOf(vals.next()).toLowerCase(Locale.ROOT));}
            finally{vals.close();}
        }
        for(String dn:splitDns(userGroupDns)){
            if(direct.contains(dn.toLowerCase(Locale.ROOT))||nested(context,userDn,dn))
                return "USER";
        }
        for(String dn:splitDns(adminGroupDns)){
            if(direct.contains(dn.toLowerCase(Locale.ROOT))||nested(context,userDn,dn))
                return "ADMIN";
        }
        return null;
    }
    private static boolean nested(InitialLdapContext ctx,String userDn,String groupDn) throws NamingException {
        SearchControls c=new SearchControls();
        c.setSearchScope(SearchControls.OBJECT_SCOPE);
        c.setCountLimit(1);
        c.setTimeLimit(4500);
        c.setReturningAttributes(new String[]{"distinguishedName"});
        String filter="(&(objectCategory=person)(objectClass=user)(memberOf:"
            +MATCHING_RULE_CHAIN+":="+escape(groupDn)+"))";
        try(NamingResults rows=new NamingResults(ctx.search(userDn,filter,c))){
            return rows.rows.hasMore();
        }
    }
    private static Set<String> splitDns(String config) {
        Set<String> result=new LinkedHashSet<>();
        if(config!=null)for(String value:config.split("\\R"))
            if(!value.isBlank())result.add(value.trim());
        return result;
    }
    static String escape(String text){
        return text.replace("\\","\\5c").replace("*","\\2a")
            .replace("(","\\28").replace(")","\\29").replace("\u0000","\\00");
    }
    private static final class NamingResults implements AutoCloseable {
        final NamingEnumeration<SearchResult> rows;
        NamingResults(NamingEnumeration<SearchResult> rows){this.rows=rows;}
        @Override public void close() throws NamingException{rows.close();}
    }
}
