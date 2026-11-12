package de.nikoit.sibyl.auth;

import de.nikoit.sibyl.addons.ad.SibylAdApi;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Core authentication boundary. Local admin accounts are never controlled
 * by Active Directory; all AD authentication is delegated to Sibyl.ad.
 */
@Component
public class SibylAuthenticationProvider implements AuthenticationProvider {
    private final DatabaseUserDetailsService users;
    private final DaoAuthenticationProvider local;
    private final SibylAdApi ad;

    public SibylAuthenticationProvider(DatabaseUserDetailsService users,
            PasswordEncoder encoder,SibylAdApi ad) {
        this.users=users;
        this.local=new DaoAuthenticationProvider(users);
        this.local.setPasswordEncoder(encoder);
        this.ad=ad;
    }

    @Override public Authentication authenticate(Authentication input) {
        String name=input.getName();
        if(name==null||name.isBlank())throw new BadCredentialsException("Invalid credentials");
        if(users.isLocalUsername(name))return local.authenticate(input);
        String password=input.getCredentials() instanceof String p?p:"";
        SibylAdApi.Login result=ad.authenticate(name,password);
        String localPart=result.username().contains("@")
            ?result.username().substring(0,result.username().indexOf('@')):result.username();
        if(users.isLocalUsername(localPart))
            throw new BadCredentialsException("AD account conflicts with a local Sibyl account");
        return UsernamePasswordAuthenticationToken.authenticated(result.username(),null,
            List.of(new SimpleGrantedAuthority("ROLE_"+result.role())));
    }
    @Override public boolean supports(Class<?> type) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
    }
}
